/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client;

import com.google.gson.JsonObject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.diagnostics.WorkspaceDiagnosticManager;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticHandler;
import com.redhat.devtools.lsp4ij.features.progress.LSPProgressManager;
import com.redhat.devtools.lsp4ij.internal.PsiFileCancelChecker;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import com.redhat.devtools.lsp4ij.settings.GlobalLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LSP {@link LanguageClient} implementation for IntelliJ.
 */
public class LanguageClientImpl implements LanguageClient, Disposable {

    private final @NotNull Project project;
    private Consumer<PublishDiagnosticsParams> diagnosticHandler;

    private @Nullable LanguageServer server;
    private @NotNull LanguageServerWrapper wrapper;
    private volatile boolean disposed;
    private WorkspaceDiagnosticManager workspaceDiagnostics;

    private Runnable didChangeConfigurationListener;

    @NotNull
    private final LSPProgressManager progressManager;

    private final SettingsLanguageListener languageServerStartedListener;

    public LanguageClientImpl(@NotNull Project project) {
        this.project = project;
        this.progressManager = new LSPProgressManager();
        this.languageServerStartedListener = new SettingsLanguageListener(this, project);
        GlobalLanguageServerSettings.getInstance().addSettingsListener(languageServerStartedListener);
        ProjectLanguageServerSettings.getInstance(getProject()).addSettingsListener(languageServerStartedListener);
    }

    public @NotNull Project getProject() {
        return project;
    }

    @ApiStatus.Internal
    public void setServerWrapper(@NotNull LanguageServerWrapper wrapper) {
        this.wrapper = wrapper;
    }

    public @NotNull LanguageServerDefinition getServerDefinition() {
        return wrapper.getServerDefinition();
    }

    @NotNull
    public LSPClientFeatures getClientFeatures() {
        return wrapper.getClientFeatures();
    }

    /**
     * Attaches this client to a server and prepares diagnostic and progress
     * handling.
     *
     * @param server the language server for this client connection
     */
    public final void connect(@NotNull LanguageServer server) {
        this.server = server;
        this.diagnosticHandler = new LSPDiagnosticHandler(wrapper);
        this.progressManager.connect(server, wrapper);
        this.workspaceDiagnostics = new WorkspaceDiagnosticManager(server.getWorkspaceService(),
                AppExecutorUtil.createBoundedScheduledExecutorService("LSP workspace diagnostics", 1),
                this::matchWorkspaceDiagnosticFile, this::applyWorkspaceDiagnosticReport,
                wrapper::clearClosedDocumentDiagnostics);
    }

    protected final @Nullable LanguageServer getLanguageServer() {
        return server;
    }

    /**
     * Queues diagnostic provider changes from the server's capabilities.
     * Does nothing before connection or after disposal.
     */
    @ApiStatus.Internal
    public void updateWorkspaceDiagnosticProviders() {
        if (!isDisposed() && workspaceDiagnostics != null) {
            workspaceDiagnostics.updateProviders(getClientFeatures().getDiagnosticFeature()
                    .getDiagnosticCapabilityRegistry().getWorkspaceDiagnosticIdentifiers());
        }
    }

    private CompletableFuture<Boolean> matchWorkspaceDiagnosticFile(String uri) {
        if (isDisposed()) {
            return CompletableFuture.completedFuture(false);
        }
        var features = getClientFeatures();
        VirtualFile file = FileUriSupport.findFileByUri(uri, features);
        if (file == null || !ReadAction.compute(() -> !isDisposed() && file.isValid() && features.isEnabled(file))) {
            return CompletableFuture.completedFuture(false);
        }
        // A server may report every workspace file, including files routed to
        // another server by a DocumentMatcher. Feature enablement alone is not
        // sufficient: most clients keep its default (true).
        return LanguageServiceAccessor.getInstance(project).isFileMappedToServer(file, getServerDefinition());
    }

    private boolean applyWorkspaceDiagnosticReport(String identifier, WorkspaceDocumentDiagnosticReport report) {
        if (isDisposed()) {
            return false;
        }
        String uri = report.isLeft() ? report.getLeft().getUri() : report.getRight().getUri();
        Integer version = report.isLeft() ? report.getLeft().getVersion() : report.getRight().getVersion();
        var features = getClientFeatures();
        VirtualFile file = FileUriSupport.findFileByUri(uri, features);
        // Matching may have completed after the file or connection changed.
        if (file == null || !ReadAction.compute(() -> !isDisposed() && file.isValid() && features.isEnabled(file))) {
            return false;
        }
        var opened = wrapper.getOpenedDocument(FileUriSupport.getFileUri(file, features));
        var synchronizer = opened != null ? opened.getSynchronizer() : null;
        if (synchronizer != null && version != null && version < synchronizer.getVersion()) {
            return false;
        }
        if (synchronizer != null && synchronizer.isPullDiagnosticsSupported()
                && Objects.equals(identifier, features.getDiagnosticFeature().getDiagnosticIdentifier())) {
            if (report.isLeft()) {
                // Document pulls take precedence. Re-pull an unchanged open file to
                // reflect cross-file changes, without overwriting newer editor diagnostics.
                synchronizer.refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH);
            }
        } else if (report.isLeft()) {
            wrapper.updateDiagnostics(file, identifier != null ? identifier : LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER,
                    report.getLeft().getItems(), version);
        }
        return true;
    }

    @Override
    public void telemetryEvent(Object object) {
        // TODO
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return ServerMessageHandler.showMessageRequest(getServerDefinition().getDisplayName(), requestParams, getProject());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        ServerMessageHandler.showMessage(getServerDefinition().getDisplayName(), messageParams, getProject());
    }

    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        return ServerMessageHandler.showDocument(params, getClientFeatures(), getProject());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        this.diagnosticHandler.accept(diagnostics);
    }

    @Override
    public void logMessage(MessageParams message) {
        CompletableFuture.runAsync(() -> ServerMessageHandler.logMessage(getServerDefinition(), message, getProject()));
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        CompletableFuture<ApplyWorkspaceEditResponse> future = new CompletableFuture<>();
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            LSPIJUtils.applyWorkspaceEdit(params.getEdit());
            future.complete(new ApplyWorkspaceEditResponse(true));
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.runAsync(() -> wrapper.registerCapability(params));
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFuture.runAsync(() -> wrapper.unregisterCapability(params));
    }

    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        return CompletableFuture.supplyAsync(() ->
            wrapper.getClientFeatures()
                .getWorkspaceFolderFeature()
                .getInitialWorkspaceFolders(project)
        );
    }


    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return refreshEditorFeatures(EditorFeatureType.CODE_VISION);
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return refreshEditorFeatures(EditorFeatureType.DECLARATIVE_INLAY_HINT);
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return refreshEditorFeatures(EditorFeatureType.SEMANTIC_TOKENS);
    }

    private CompletableFuture<Void> refreshEditorFeatures(EditorFeatureType feature) {
        if (wrapper != null && !isDisposed() && !wrapper.isDisposed()) {
            EditorFeatureManager.getInstance(project).refreshEditorFeatures(this,
                    () -> wrapper.getOpenedDocuments().stream().map(OpenedDocument::getFile).toList(), feature,
                    () -> {
                        if (isDisposed() || wrapper.isDisposed()) {
                            throw new CancellationException();
                        }
                    });
        }
        // Acknowledge scheduling. Coalescing superseded work must not fail an LSP refresh RPC.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Requests fresh diagnostics for open documents and workspace providers.
     *
     * @return a future completed after refresh work is dispatched, without
     *         waiting for diagnostic results
     */
    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return CompletableFuture.runAsync(() -> {
            if (!isDisposed()) {
                refreshDiagnosticsForAllOpenedFiles();
                if (workspaceDiagnostics != null) {
                    workspaceDiagnostics.refresh();
                }
            }
        });
    }

    private void refreshDiagnosticsForAllOpenedFiles() {
        // Received request 'workspace/diagnostic/refresh
        for (var openedDocument : wrapper.getOpenedDocuments()) {
            if (openedDocument.getSynchronizer() != null) {
                openedDocument.getSynchronizer()
                        .refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH);
            }
        }
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return progressManager.createProgress(params);
    }

    /**
     * Routes progress notifications to workspace diagnostics and progress
     * tracking.
     *
     * @param params the progress notification received from the language server
     */
    @Override
    public void notifyProgress(ProgressParams params) {
        if (workspaceDiagnostics != null) {
            workspaceDiagnostics.notifyProgress(params);
        }
        progressManager.notifyProgress(params);
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
        return CompletableFuture.supplyAsync(() -> {
            // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_configuration
            List<Object> settings = new ArrayList<>();
            for (ConfigurationItem item : params.getItems()) {
                String section = item.getSection();
                Object result = findSettings(section);
                // The response is the configuration setting or null, according to the spec:
                //  - If a scope URI is provided the client should return the setting scoped to the provided resource.
                //  - If the client can’t provide a configuration setting for a given scope then null needs to be present in the returned array.
                settings.add(result);
            }
            return settings;
        });
    }

    /**
     * Returns the settings retrieved by the given section and null otherwise.
     *
     * <p>
     * This method is implemented by default to work with JsonObject ({@link LanguageClientImpl#createSettings()}
     * must return an instance of {@link JsonObject} but this method can be overridden to return another structure.
     * </p>
     *
     * @param section the section. In the base implementation, a null section indicates a request for the entire
     *                settings object.
     * @return the settings retrieved by a valid section and null otherwise.
     */
    protected Object findSettings(@Nullable String section) {
        var config = createSettings();
        if (config instanceof JsonObject json) {
            if (section == null || section.isEmpty()) {
                return config;
            }
            return findSettings(section, json);
        }
        return null;
    }

    protected static Object findSettings(String section, JsonObject jsonObject) {
        return SettingsHelper.findSettings(section, jsonObject);
    }

    /**
     * Create the settings to send via 'workspace/didChangeConfiguration' and null otherwise.
     *
     * @return the settings to send via 'workspace/didChangeConfiguration' and null otherwise.
     */
    @Nullable
    protected Object createSettings() {
        String languageServerId = getServerDefinition().getId();
        var project = getProject();
        var settings = GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(languageServerId);
        return settings != null ? settings.getLanguageServerConfiguration(project) : null;
    }

    protected synchronized Runnable getDidChangeConfigurationListener() {
        if (didChangeConfigurationListener != null) {
            return didChangeConfigurationListener;
        }
        didChangeConfigurationListener = this::triggerChangeConfiguration;
        return didChangeConfigurationListener;
    }

    public void triggerChangeConfiguration() {
        LanguageServer languageServer = getLanguageServer();
        if (languageServer == null) {
            return;
        }
        Object settings = createSettings();
        if (settings == null) {
            // LSP DidChangeConfigurationParams requires a non-null settings
            settings = new JsonObject();
        }
        DidChangeConfigurationParams params = new DidChangeConfigurationParams(settings);
        languageServer.getWorkspaceService().didChangeConfiguration(params);
    }


    /**
     * Callback invoked when language server status changed.
     * <p>
     * Since language client doesn't exist during some status, this callback receives only:
     *
     * <ul>
     *     <li>{@link ServerStatus#stopping}</li>
     *     <li>{@link ServerStatus#started}</li>
     * </ul>
     *
     * <p>
     *     If you need to track all status, you can do that by implementing {@link LSPClientFeatures#handleServerStatusChanged(ServerStatus)}.
     * </p>
     *
     * <p>
     *     This method could be implemented to send 'workspace/didChangeConfiguration' (by calling triggerChangeConfiguration)
     *     when server is started.
     *     The implementation must be fast or execute asynchronously to avoid deteriorate startup of language server performance.
     * </p>
     *
     * @param serverStatus server status
     */
    public void handleServerStatusChanged(@NotNull ServerStatus serverStatus) {
        // Do nothing
    }

    /**
     * Disposes diagnostic and progress handling and removes settings listeners.
     */
    @Override
    public void dispose() {
        this.disposed = true;
        if (workspaceDiagnostics != null) {
            workspaceDiagnostics.dispose();
        }
        this.progressManager.dispose();
        GlobalLanguageServerSettings.getInstance().removeSettingsListener(languageServerStartedListener);
        ProjectLanguageServerSettings.getInstance(getProject()).removeSettingsListener(languageServerStartedListener);
    }

    public boolean isDisposed() {
        return disposed || getProject().isDisposed();
    }

}

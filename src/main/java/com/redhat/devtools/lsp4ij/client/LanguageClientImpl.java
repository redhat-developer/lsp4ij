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
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticHandler;
import com.redhat.devtools.lsp4ij.features.progress.LSPProgressManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * LSP {@link LanguageClient} implementation for IntelliJ.
 */
public class LanguageClientImpl implements LanguageClient, Disposable {
    private final Project project;
    private Consumer<PublishDiagnosticsParams> diagnosticHandler;

    private LanguageServer server;
    private LanguageServerWrapper wrapper;

    private boolean disposed;

    private Runnable didChangeConfigurationListener;

    @NotNull
    private final LSPProgressManager progressManager;

    public LanguageClientImpl(Project project) {
        this.project = project;
        progressManager = new LSPProgressManager();
    }

    public Project getProject() {
        return project;
    }

    public final void connect(@NotNull LanguageServer server, @NotNull LanguageServerWrapper wrapper) {
        this.server = server;
        this.wrapper = wrapper;
        this.diagnosticHandler = new LSPDiagnosticHandler(wrapper);
        this.progressManager.connect(server, wrapper);
    }

    protected final LanguageServer getLanguageServer() {
        return server;
    }

    public LanguageServerDefinition getServerDefinition() {
        return wrapper.getServerDefinition();
    }

    public LSPClientFeatures getClientFeatures() {
        return wrapper.getClientFeatures();
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
        return CompletableFuture.supplyAsync(() -> LSPIJUtils.toWorkspaceFolders(project));
    }


    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        return CompletableFuture.runAsync(() -> {
            if (wrapper == null) {
                return;
            }
            refreshCodeLensForAllOpenedFiles();
        });
    }

    private void refreshCodeLensForAllOpenedFiles() {
        for (var openedDocument : wrapper.getOpenedDocuments()) {
            VirtualFile file = openedDocument.getFile();
            EditorFeatureManager.getInstance(getProject())
                    .refreshEditorFeature(file, EditorFeatureType.CODE_VISION, true);
        }
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints() {
        return CompletableFuture.runAsync(() -> {
            if (wrapper == null) {
                return;
            }
            refreshInlayHintsForAllOpenedFiles();
        });
    }

    private void refreshInlayHintsForAllOpenedFiles() {
        for (var openedDocument : wrapper.getOpenedDocuments()) {
            VirtualFile file = openedDocument.getFile();
            EditorFeatureManager efm = EditorFeatureManager.getInstance(getProject());
            efm.refreshEditorFeature(file, EditorFeatureType.INLAY_HINT, true);
            efm.refreshEditorFeature(file, EditorFeatureType.DECLARATIVE_INLAY_HINT, true);
        }
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        return CompletableFuture.runAsync(() -> {
            if (wrapper == null) {
                return;
            }
            refreshSemanticTokensForAllOpenedFiles();
        });
    }

    private void refreshSemanticTokensForAllOpenedFiles() {
        // Received request 'workspace/semanticTokens/refresh
        ReadAction.nonBlocking((Callable<Void>) () -> {
                    for (var openedDocument : wrapper.getOpenedDocuments()) {
                        VirtualFile file = openedDocument.getFile();
                        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                        if (psiFile != null) {
                            var fileSupport = LSPFileSupport.getSupport(psiFile);
                            // Evict the semantic tokens cache
                            fileSupport.getSemanticTokensSupport().cancel();
                            // Refresh the UI
                            fileSupport.restartDaemonCodeAnalyzerWithDebounce();
                        }
                    }
                    return null;
                }).coalesceBy(this)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics() {
        return CompletableFuture.runAsync(() -> {
            if (wrapper == null) {
                return;
            }
            refreshDiagnosticsForAllOpenedFiles();
        });
    }

    private void refreshDiagnosticsForAllOpenedFiles() {
        // Received request 'workspace/diagnostic/refresh
        for (var openedDocument : wrapper.getOpenedDocuments()) {
            openedDocument.getSynchronizer().refreshPullDiagnostic(DocumentContentSynchronizer.RefreshPullDiagnosticOrigin.ON_WORKSPACE_REFRESH);
        }
    }

    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return progressManager.createProgress(params);
    }

    @Override
    public void notifyProgress(ProgressParams params) {
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
            if (section == null) {
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
        return null;
    }

    protected synchronized Runnable getDidChangeConfigurationListener() {
        if (didChangeConfigurationListener != null) {
            return didChangeConfigurationListener;
        }
        didChangeConfigurationListener = this::triggerChangeConfiguration;
        return didChangeConfigurationListener;
    }

    protected void triggerChangeConfiguration() {
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

    @Override
    public void dispose() {
        this.disposed = true;
        this.progressManager.dispose();
    }

    public boolean isDisposed() {
        return disposed || getProject().isDisposed();
    }

}

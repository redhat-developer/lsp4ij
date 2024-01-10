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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerMessageHandler;
import com.redhat.devtools.lsp4ij.operations.diagnostics.LSPDiagnosticHandler;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    public LanguageClientImpl(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public final void connect(LanguageServer server, LanguageServerWrapper wrapper) {
        this.server = server;
        this.wrapper = wrapper;
        this.diagnosticHandler = new LSPDiagnosticHandler(wrapper);
    }

    protected final LanguageServer getLanguageServer() {
        return server;
    }

    @Override
    public void telemetryEvent(Object object) {
        // TODO
    }

    @Override
    public final CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return ServerMessageHandler.showMessageRequest(wrapper, requestParams);
    }

    @Override
    public final void showMessage(MessageParams messageParams) {
        ServerMessageHandler.showMessage(wrapper.serverDefinition.getDisplayName(), messageParams);
    }

    @Override
    public final void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        this.diagnosticHandler.accept(diagnostics);
    }

    @Override
    public final void logMessage(MessageParams message) {
        CompletableFuture.runAsync(() -> ServerMessageHandler.logMessage(wrapper, message));
    }

    @Override
    public final CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        CompletableFuture<ApplyWorkspaceEditResponse> future = new CompletableFuture<>();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
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
        Project project = wrapper.getProject();
        if (project != null) {
            List<WorkspaceFolder> folders = Arrays.asList(LSPIJUtils.toWorkspaceFolder(project));
            return CompletableFuture.completedFuture(folders);
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public void dispose() {
        this.disposed = true;
    }

    public boolean isDisposed() {
        return disposed || getProject().isDisposed();
    }

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
            return;
        }
        DidChangeConfigurationParams params = new DidChangeConfigurationParams(settings);
        languageServer.getWorkspaceService().didChangeConfiguration(params);
    }

}

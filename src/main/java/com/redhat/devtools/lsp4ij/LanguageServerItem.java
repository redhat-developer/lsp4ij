/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import com.redhat.devtools.lsp4ij.server.Lease;
import com.redhat.devtools.lsp4ij.server.ServerWasStoppedException;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Item which stores the initialized LSP4j language server and the language server wrapper.
 */
public class LanguageServerItem {

    private final LanguageServerWrapper serverWrapper;
    private final LanguageServer server;
    private final ServerCapabilities serverCapabilities;

    public LanguageServerItem(LanguageServer server, LanguageServerWrapper serverWrapper) {
        this.server = server;
        this.serverWrapper = serverWrapper;
        this.serverCapabilities = serverWrapper.getServerCapabilities();
    }

    /**
     * Creates a 'lease' on this language server which expresses the intent of
     * the caller for the server to be 'kept alive' for as long as this
     * lease has not been disposed.
     * <p>
     * The server may still be terminated under some exceptional circumstances, for example
     * when:
     * - the server crashed
     * - the user explicitly terminated the server from the lsp4ij user interface
     * - the IDE is shutting down
     * <p>
     * Under 'normal circumstances' however the server will not be shutdown due to 'inactivity'
     * or because there are no more open editors.
     * <p>
     * In other words calling this methods creates a 'similar demand' on the language
     * server's lifecycle as would opening a document in an editor.
     */
    public Lease<LanguageServerItem> keepAlive() {
        serverWrapper.incrementKeepAlive();
        return new Lease<>() {
            final AtomicBoolean isDisposed = new AtomicBoolean();

            @Override
            public LanguageServerItem get() throws ServerWasStoppedException, IllegalStateException {
                if (isDisposed.get()) {
                    throw new IllegalStateException("Bug: trying to use an already disposed Lease");
                }
                var item = LanguageServerItem.this;
                if (item.serverWrapper.getServerStatus() == ServerStatus.started) {
                    return LanguageServerItem.this;
                }
                //TODO (maybe): Can `item.serverWrapper.getServerError()` provide a more informative error message?
                var serverDefinition = item.getServerDefinition();
                throw new ServerWasStoppedException("The server was stopped unexpectedly '"
                        + serverDefinition.getId() + "' (pid=" + item.serverWrapper.getCurrentProcessId()+")");
            }
            @Override
            public void dispose() {
                 if (!isDisposed.getAndSet(true)) {
                     serverWrapper.decrementKeepAlive();
                 }
            }
        };
    }

    /**
     * Returns the project.
     *
     * @return the project.
     */
    @NotNull
    public Project getProject() {
        return getServerWrapper().getProject();
    }

    /**
     * Returns the LSP4J language server initialized when the LanguageServerItem was created.
     * <p>
     * Storing this instance is not recommended, as there's no guarantee the language server instance will still be alive in the long term.
     * Thus, it is recommended to use {@link #getInitializedServer()} instead, which guarantees the server instance will be initialized at the time of that call.
     * </p>
     *
     * @return the LSP4J language server initialized when the LanguageServerItem was created.
     */
    public LanguageServer getServer() {
        return server;
    }

    /**
     * Returns the LSP4J language server, guaranteed to be initialized at that point.
     *
     * @return the LSP4J language server, guaranteed to be initialized at that point.
     */
    @NotNull
    public CompletableFuture<LanguageServer> getInitializedServer() {
        return getServerWrapper().getInitializedServer();
    }

    /**
     * Returns the language server wrapper.
     *
     * @return the language server wrapper.
     */
    @ApiStatus.Internal
    public LanguageServerWrapper getServerWrapper() {
        return serverWrapper;
    }

    /**
     * Returns the server definition.
     *
     * @return the server definition.
     */
    @NotNull
    public LanguageServerDefinition getServerDefinition() {
        return getServerWrapper().getServerDefinition();
    }

    /**
     * Returns the LSP client features.
     *
     * @return the LSP client features.
     */
    public LSPClientFeatures getClientFeatures() {
        return getServerWrapper().getClientFeatures();
    }

    /**
     * Returns the server capabilities of the language server.
     *
     * @return the server capabilities of the language server.
     */
    public ServerCapabilities getServerCapabilities() {
        return serverCapabilities;
    }

    /**
     * Returns true if the language server can support range formatting and false otherwise.
     *
     * @return true if the language server can support range formatting and false otherwise.
     */
    public boolean isDocumentRangeFormattingSupported() {
        return isDocumentRangeFormattingSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support range formatting and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support range formatting and false otherwise.
     */
    public static boolean isDocumentRangeFormattingSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getDocumentRangeFormattingProvider());
    }

    /**
     * Returns true if the language server can support prepare rename and false otherwise.
     *
     * @return true if the language server can support prepare rename and false otherwise.
     */
    public boolean isPrepareRenameSupported() {
        return isPrepareRenameSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support prepare rename and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support prepare rename and false otherwise.
     */
    public static boolean isPrepareRenameSupported(@Nullable ServerCapabilities serverCapabilities) {
        Either<Boolean, RenameOptions> renameProvider = serverCapabilities != null ? serverCapabilities.getRenameProvider() : null;
        if (renameProvider != null && renameProvider.isRight()) {
            return hasCapability(renameProvider.getRight().getPrepareProvider());
        }
        return false;
    }

    public boolean isWillRenameFilesSupported(PsiFile file) {
        return serverWrapper.isWillRenameFilesSupported(file);
    }

    public static boolean isWillRenameFilesSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                serverCapabilities.getWorkspace() != null &&
                serverCapabilities.getWorkspace().getFileOperations() != null &&
                serverCapabilities.getWorkspace().getFileOperations().getWillRename() != null;
    }

    /**
     * Returns true if the given LSP command is supported by the language server and false otherwise.
     *
     * @param command the LSP command.
     * @return true if the given LSP command is supported by the language server and false otherwise.
     */
    public boolean supportsCommand(@Nullable Command command) {
        if (command == null) {
            return false;
        }
        ServerCapabilities serverCapabilities = getServerCapabilities();
        if (serverCapabilities == null) {
            return false;
        }
        ExecuteCommandOptions provider = serverCapabilities.getExecuteCommandProvider();
        return provider != null && provider.getCommands().contains(command.getCommand());
    }

    /**
     * Returns the LSP {@link TextDocumentService} of the language server.
     *
     * @return the LSP {@link TextDocumentService} of the language server.
     */
    public TextDocumentService getTextDocumentService() {
        return getServer().getTextDocumentService();
    }

    /**
     * Returns the LSP {@link WorkspaceService} of the language server.
     *
     * @return the LSP {@link WorkspaceService} of the language server.
     */
    public WorkspaceService getWorkspaceService() {
        return getServer().getWorkspaceService();
    }

    private static boolean hasCapability(final Either<Boolean, ?> eitherCapability) {
        if (eitherCapability == null) {
            return false;
        }
        return eitherCapability.isRight() || hasCapability(eitherCapability.getLeft());
    }

    private static boolean hasCapability(Boolean capability) {
        return capability != null && capability;
    }

    public SemanticTokensColorsProvider getSemanticTokensColorsProvider() {
        return getClientFeatures().getSemanticTokensFeature();
    }

}
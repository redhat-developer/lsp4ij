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

import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.Nullable;

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
     * Returns the LSP4j language server.
     *
     * @return the LSP4j language server.
     */
    public LanguageServer getServer() {
        return server;
    }

    /**
     * Returns the language server wrapper.
     *
     * @return the language server wrapper.
     */
    public LanguageServerWrapper getServerWrapper() {
        return serverWrapper;
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
     * Returns true if the language server can support resolve completion and false otherwise.
     *
     * @return true if the language server can support resolve completion and false otherwise.
     */
    public boolean isResolveCompletionSupported() {
        return isResolveCompletionSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support signature help and false otherwise.
     *
     * @return true if the language server can support signature help and false otherwise.
     */
    public boolean isSignatureHelpSupported() {
        return isSignatureHelpSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support resolve code lens and false otherwise.
     *
     * @return true if the language server can support resolve code lens and false otherwise.
     */
    public boolean isResolveCodeLensSupported() {
        return isResolveCodeLensSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support resolve inlay hint and false otherwise.
     *
     * @return true if the language server can support resolve inlay hint and false otherwise.
     */
    public boolean isResolveInlayHintSupported() {
        return isResolveInlayHintSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support references and false otherwise.
     *
     * @return true if the language server can support references and false otherwise.
     */
    public boolean isReferencesSupported() {
        return isReferencesSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support implementation and false otherwise.
     *
     * @return true if the language server can support implementation and false otherwise.
     */
    public boolean isImplementationSupported() {
        return isImplementationSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support declaration and false otherwise.
     *
     * @return true if the language server can support declaration and false otherwise.
     */
    public boolean isDeclarationSupported() {
        return isDeclarationSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support definition and false otherwise.
     *
     * @return true if the language server can support definition and false otherwise.
     */
    public boolean isDefinitionSupported() {
        return isDefinitionSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support type definition and false otherwise.
     *
     * @return true if the language server can support type definition and false otherwise.
     */
    public boolean isTypeDefinitionSupported() {
        return isTypeDefinitionSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support formatting and false otherwise.
     *
     * @return true if the language server can support formatting and false otherwise.
     */
    public boolean isDocumentFormattingSupported() {
        return isDocumentFormattingSupported(getServerCapabilities());
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
     * Returns true if the language server can support completion and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support completion and false otherwise.
     */
    public static boolean isCompletionSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                serverCapabilities.getCompletionProvider() != null;
    }

    /**
     * Returns true if the language server can support resolve completion and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support resolve completion and false otherwise.
     */
    public static boolean isResolveCompletionSupported(@Nullable ServerCapabilities serverCapabilities) {
        var completionProvider = serverCapabilities != null ? serverCapabilities.getCompletionProvider() : null;
        return completionProvider != null && hasCapability(completionProvider.getResolveProvider());
    }

    /**
     * Returns true if the language server can support signature help and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support signature help and false otherwise.
     */
    public static boolean isSignatureHelpSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                serverCapabilities.getSignatureHelpProvider() != null;
    }

    /**
     * Returns true if the language server can support code lens and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support code lens and false otherwise.
     */
    public static boolean isCodeLensSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                serverCapabilities.getCodeLensProvider() != null;
    }

    /**
     * Returns true if the language server can support resolve code lens and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support resolve code lens and false otherwise.
     */
    public static boolean isResolveCodeLensSupported(@Nullable ServerCapabilities serverCapabilities) {
        var codeLensProvider = serverCapabilities != null ? serverCapabilities.getCodeLensProvider() : null;
        return codeLensProvider != null && hasCapability(codeLensProvider.getResolveProvider());
    }

    /**
     * Returns true if the language server can support inlay hint and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support inlay hint and false otherwise.
     */
    public static boolean isInlayHintSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getInlayHintProvider());
    }

    /**
     * Returns true if the language server can support resolve inlay hint and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support resolve inlay hint and false otherwise.
     */
    public static boolean isResolveInlayHintSupported(@Nullable ServerCapabilities serverCapabilities) {
        var inlayHintProvider = serverCapabilities != null ? serverCapabilities.getInlayHintProvider() : null;
        if (inlayHintProvider != null && inlayHintProvider.isRight()) {
            return hasCapability(inlayHintProvider.getRight().getResolveProvider());
        }
        return false;
    }

    /**
     * Returns true if the language server can support color and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support color and false otherwise.
     */
    public static boolean isColorSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getColorProvider());
    }

    /**
     * Returns true if the language server can support declaration and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support declaration and false otherwise.
     */
    public static boolean isDeclarationSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getDeclarationProvider());
    }

    /**
     * Returns true if the language server can support definition and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support definition and false otherwise.
     */
    public static boolean isDefinitionSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getDefinitionProvider());
    }

    /**
     * Returns true if the language server can support type definition and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support type definition and false otherwise.
     */
    public static boolean isTypeDefinitionSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getTypeDefinitionProvider());
    }

    /**
     * Returns true if the language server can support document highlight and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support document highlight and false otherwise.
     */
    public static boolean isDocumentHighlightSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getDocumentHighlightProvider());
    }

    /**
     * Returns true if the language server can support document link and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support document link and false otherwise.
     */
    public static boolean isDocumentLinkSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                serverCapabilities.getDocumentLinkProvider() != null;
    }

    /**
     * Returns true if the language server can support document link and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support document link and false otherwise.
     */
    public static boolean isHoverSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getHoverProvider());
    }

    /**
     * Returns true if the language server can support references and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support references and false otherwise.
     */
    public static boolean isReferencesSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getReferencesProvider());
    }

    /**
     * Returns true if the language server can support references and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support references and false otherwise.
     */
    public static boolean isImplementationSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getImplementationProvider());
    }

    /**
     * Returns true if the language server can support folding and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support folding and false otherwise.
     */
    public static boolean isFoldingSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getFoldingRangeProvider());
    }

    /**
     * Returns true if the language server can support formatting and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support formatting and false otherwise.
     */
    public static boolean isDocumentFormattingSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getDocumentFormattingProvider());
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
     * Returns true if the language server can support code action and false otherwise.
     *
     * @return true if the language server can support code action and false otherwise.
     */
    public boolean isCodeActionSupported() {
        return isCodeActionSupported(getServerCapabilities());
    }

    /**
     * Returns true if the language server can support code action and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support code action and false otherwise.
     */
    public static boolean isCodeActionSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
            hasCapability(serverCapabilities.getCodeActionProvider());
    }

    /**
     * Returns true if the language server can support resolve code action and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support resolve code action and false otherwise.
     */
    public static boolean isCodeActionResolveSupported(@Nullable ServerCapabilities serverCapabilities) {
        Either<Boolean, CodeActionOptions> codeActionProvider = serverCapabilities != null ? serverCapabilities.getCodeActionProvider() : null;
        if (codeActionProvider != null && codeActionProvider.isRight()) {
            return hasCapability(codeActionProvider.getRight().getResolveProvider());
        }
        return false;
    }


    /**
     * Returns true if the language server can support rename and false otherwise.
     *
     * @param serverCapabilities the server capabilities.
     * @return true if the language server can support rename and false otherwise.
     */
    public static boolean isRenameSupported(@Nullable ServerCapabilities serverCapabilities) {
        return serverCapabilities != null &&
                hasCapability(serverCapabilities.getRenameProvider());
    }

    /**
     * Returns true if the language server can support prepare rename and false otherwise.
     *
     * @return true if the language server can support prepare rename and false otherwise.
     */
    public  boolean isPrepareRenameSupported() {
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
}
/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.server.capabilities.TextDocumentServerCapabilityRegistry;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP  client features.
 */
@ApiStatus.Experimental
public class LSPClientFeatures implements Disposable {

    private LanguageServerWrapper serverWrapper;

    private LSPCodeActionFeature codeActionFeature;

    private LSPCodeLensFeature codeLensFeature;

    private LSPDocumentColorFeature documentColorFeature;

    private LSPCompletionFeature completionFeature;

    private LSPDeclarationFeature declarationFeature;

    private LSPDefinitionFeature definitionFeature;

    private LSPDocumentLinkFeature documentLinkFeature;

    private LSPDocumentHighlightFeature documentHighlightFeature;

    private LSPDocumentSymbolFeature documentSymbolFeature;

    private LSPDiagnosticFeature diagnosticFeature;

    private LSPFoldingRangeFeature foldingRangeFeature;

    private LSPFormattingFeature formattingFeature;

    private LSPImplementationFeature implementationFeature;

    private LSPInlayHintFeature inlayHintFeature;

    private LSPHoverFeature hoverFeature;

    private LSPReferencesFeature referencesFeature;

    private LSPRenameFeature renameFeature;

    private LSPSemanticTokensFeature semanticTokensFeature;

    private LSPSignatureHelpFeature signatureHelpFeature;

    private LSPTypeDefinitionFeature typeDefinitionFeature;

    private LSPUsageFeature usageFeature;

    private LSPWorkspaceSymbolFeature workspaceSymbolFeature;

    /**
     * Returns the project.
     *
     * @return the project.
     */
    @NotNull
    public final Project getProject() {
        return getServerWrapper().getProject();
    }

    /**
     * Returns the language server definition.
     *
     * @return the language server definition.
     */
    @NotNull
    public final LanguageServerDefinition getServerDefinition() {
        return getServerWrapper().getServerDefinition();
    }

    /**
     * Returns true if the given language server id matches the server definition and false otherwise.
     *
     * @param languageServerId the language server id.
     * @return true if the given language server id matches the server definition and false otherwise.
     */
    public boolean isServerDefinition(@NotNull String languageServerId) {
        return languageServerId.equals(getServerDefinition().getId());
    }

    /**
     * Returns the server status.
     *
     * @return the server status.
     */
    @NotNull
    public final ServerStatus getServerStatus() {
        return getServerWrapper().getServerStatus();
    }

    /**
     * Returns the LSP4J language server.
     *
     * @return the LSP4J language server.
     */
    @Nullable
    public final LanguageServer getLanguageServer() {
        return getServerWrapper().getLanguageServer();
    }

    /**
     * Returns true if the server is kept alive even if all files associated with the language server are closed and false otherwise.
     *
     * @return true if the server is kept alive even if all files associated with the language server are closed and false otherwise.
     */
    public boolean keepServerAlive() {
        return false;
    }

    /**
     * Returns true if the user can stop the language server in LSP console from the context menu and false otherwise.
     * <p>
     * By default, user can stop the server.
     * </p>
     *
     * @return true if the user can stop the language server in LSP console from the context menu and false otherwise.
     */
    public boolean canStopServerByUser() {
        return true;
    }

    /**
     * Returns the LSP codeAction feature.
     *
     * @return the LSP codeAction feature.
     */
    @NotNull
    public final LSPCodeActionFeature getCodeActionFeature() {
        if (codeActionFeature == null) {
            setCodeActionFeature(new LSPCodeActionFeature());
        }
        return codeActionFeature;
    }

    /**
     * Initialize the LSP codeAction feature.
     *
     * @param codeActionFeature the LSP codeAction feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setCodeActionFeature(@NotNull LSPCodeActionFeature codeActionFeature) {
        codeActionFeature.setClientFeatures(this);
        this.codeActionFeature = codeActionFeature;
        return this;
    }

    /**
     * Returns the LSP codeLens feature.
     *
     * @return the LSP codeLens feature.
     */
    @NotNull
    public final LSPCodeLensFeature getCodeLensFeature() {
        if (codeLensFeature == null) {
            setCodeLensFeature(new LSPCodeLensFeature());
        }
        return codeLensFeature;
    }

    /**
     * Initialize the LSP codeLens feature.
     *
     * @param codeLensFeature the LSP codeLens feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setCodeLensFeature(@NotNull LSPCodeLensFeature codeLensFeature) {
        codeLensFeature.setClientFeatures(this);
        this.codeLensFeature = codeLensFeature;
        return this;
    }

    /**
     * Returns the LSP color feature.
     *
     * @return the LSP color feature.
     */
    @NotNull
    public final LSPDocumentColorFeature getDocumentColorFeature() {
        if (documentColorFeature == null) {
            setDocumentColorFeature(new LSPDocumentColorFeature());
        }
        return documentColorFeature;
    }

    /**
     * Initialize the LSP color feature.
     *
     * @param documentColorFeature the LSP color feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDocumentColorFeature(@NotNull LSPDocumentColorFeature documentColorFeature) {
        documentColorFeature.setClientFeatures(this);
        this.documentColorFeature = documentColorFeature;
        return this;
    }

    /**
     * Returns the LSP completion feature.
     *
     * @return the LSP completion feature.
     */
    @NotNull
    public final LSPCompletionFeature getCompletionFeature() {
        if (completionFeature == null) {
            setCompletionFeature(new LSPCompletionFeature());
        }
        return completionFeature;
    }

    /**
     * Initialize the LSP completion feature.
     *
     * @param completionFeature the LSP completion feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setCompletionFeature(@NotNull LSPCompletionFeature completionFeature) {
        completionFeature.setClientFeatures(this);
        this.completionFeature = completionFeature;
        return this;
    }

    /**
     * Returns the LSP declaration feature.
     *
     * @return the LSP declaration feature.
     */
    @NotNull
    public final LSPDeclarationFeature getDeclarationFeature() {
        if (declarationFeature == null) {
            setDeclarationFeature(new LSPDeclarationFeature());
        }
        return declarationFeature;
    }

    /**
     * Initialize the LSP declaration feature.
     *
     * @param declarationFeature the LSP declaration feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDeclarationFeature(@NotNull LSPDeclarationFeature declarationFeature) {
        declarationFeature.setClientFeatures(this);
        this.declarationFeature = declarationFeature;
        return this;
    }

    /**
     * Returns the LSP definition feature.
     *
     * @return the LSP definition feature.
     */
    @NotNull
    public final LSPDefinitionFeature getDefinitionFeature() {
        if (definitionFeature == null) {
            setDefinitionFeature(new LSPDefinitionFeature());
        }
        return definitionFeature;
    }

    /**
     * Initialize the LSP definition feature.
     *
     * @param definitionFeature the LSP definition feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDefinitionFeature(@NotNull LSPDefinitionFeature definitionFeature) {
        definitionFeature.setClientFeatures(this);
        this.definitionFeature = definitionFeature;
        return this;
    }

    /**
     * Returns the LSP documentHighlight feature.
     *
     * @return the LSP documentHighlight feature.
     */
    @NotNull
    public final LSPDocumentHighlightFeature getDocumentHighlightFeature() {
        if (documentHighlightFeature == null) {
            setDocumentHighlightFeature(new LSPDocumentHighlightFeature());
        }
        return documentHighlightFeature;
    }

    /**
     * Initialize the LSP documentHighlight feature.
     *
     * @param documentHighlightFeature the LSP documentHighlight feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDocumentHighlightFeature(@NotNull LSPDocumentHighlightFeature documentHighlightFeature) {
        documentHighlightFeature.setClientFeatures(this);
        this.documentHighlightFeature = documentHighlightFeature;
        return this;
    }

    /**
     * Returns the LSP documentLink feature.
     *
     * @return the LSP documentLink feature.
     */
    @NotNull
    public final LSPDocumentLinkFeature getDocumentLinkFeature() {
        if (documentLinkFeature == null) {
            setDocumentLinkFeature(new LSPDocumentLinkFeature());
        }
        return documentLinkFeature;
    }

    /**
     * Initialize the LSP documentLink feature.
     *
     * @param documentLinkFeature the LSP documentLink feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDocumentLinkFeature(@NotNull LSPDocumentLinkFeature documentLinkFeature) {
        documentLinkFeature.setClientFeatures(this);
        this.documentLinkFeature = documentLinkFeature;
        return this;
    }

    /**
     * Returns the LSP documentSymbol feature.
     *
     * @return the LSP documentSymbol feature.
     */
    @NotNull
    public final LSPDocumentSymbolFeature getDocumentSymbolFeature() {
        if (documentSymbolFeature == null) {
            setDocumentSymbolFeature(new LSPDocumentSymbolFeature());
        }
        return documentSymbolFeature;
    }

    /**
     * Initialize the LSP documentSymbol feature.
     *
     * @param documentSymbolFeature the LSP documentSymbol feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setDocumentSymbolFeature(@NotNull LSPDocumentSymbolFeature documentSymbolFeature) {
        documentSymbolFeature.setClientFeatures(this);
        this.documentSymbolFeature = documentSymbolFeature;
        return this;
    }

    /**
     * Returns the LSP diagnostic feature.
     *
     * @return the LSP diagnostic feature.
     */
    @NotNull
    public final LSPDiagnosticFeature getDiagnosticFeature() {
        if (diagnosticFeature == null) {
            setDiagnosticFeature(new LSPDiagnosticFeature());
        }
        return diagnosticFeature;
    }

    /**
     * Initialize the LSP diagnostic feature.
     *
     * @param diagnosticFeature the LSP diagnostic feature.
     * @return the LSP client feature.
     */
    public LSPClientFeatures setDiagnosticFeature(@NotNull LSPDiagnosticFeature diagnosticFeature) {
        diagnosticFeature.setClientFeatures(this);
        this.diagnosticFeature = diagnosticFeature;
        return this;
    }

    /**
     * Returns the LSP foldingRange feature.
     *
     * @return the LSP foldingRange feature.
     */
    @NotNull
    public final LSPFoldingRangeFeature getFoldingRangeFeature() {
        if (foldingRangeFeature == null) {
            setFoldingRangeFeature(new LSPFoldingRangeFeature());
        }
        return foldingRangeFeature;
    }

    /**
     * Initialize the LSP foldingRange feature.
     *
     * @param foldingRangeFeature the LSP foldingRange feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setFoldingRangeFeature(@NotNull LSPFoldingRangeFeature foldingRangeFeature) {
        foldingRangeFeature.setClientFeatures(this);
        this.foldingRangeFeature = foldingRangeFeature;
        return this;
    }

    /**
     * Returns the LSP formatting feature.
     *
     * @return the LSP formatting feature.
     */
    @NotNull
    public final LSPFormattingFeature getFormattingFeature() {
        if (formattingFeature == null) {
            setFormattingFeature(new LSPFormattingFeature());
        }
        return formattingFeature;
    }

    /**
     * Initialize the LSP formatting feature.
     *
     * @param formattingFeature the LSP formatting feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setFormattingFeature(@NotNull LSPFormattingFeature formattingFeature) {
        formattingFeature.setClientFeatures(this);
        this.formattingFeature = formattingFeature;
        return this;
    }

    /**
     * Returns the LSP implementation feature.
     *
     * @return the LSP implementation feature.
     */
    @NotNull
    public final LSPImplementationFeature getImplementationFeature() {
        if (implementationFeature == null) {
            setImplementationFeature(new LSPImplementationFeature());
        }
        return implementationFeature;
    }

    /**
     * Initialize the LSP implementation feature.
     *
     * @param implementationFeature the LSP implementation feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setImplementationFeature(@NotNull LSPImplementationFeature implementationFeature) {
        implementationFeature.setClientFeatures(this);
        this.implementationFeature = implementationFeature;
        return this;
    }

    /**
     * Returns the LSP inlayHint feature.
     *
     * @return the LSP inlayHint feature.
     */
    @NotNull
    public final LSPInlayHintFeature getInlayHintFeature() {
        if (inlayHintFeature == null) {
            setInlayHintFeature(new LSPInlayHintFeature());
        }
        return inlayHintFeature;
    }

    /**
     * Initialize the LSP inlayHint feature.
     *
     * @param inlayHintFeature the LSP inlayHint feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setInlayHintFeature(@NotNull LSPInlayHintFeature inlayHintFeature) {
        inlayHintFeature.setClientFeatures(this);
        this.inlayHintFeature = inlayHintFeature;
        return this;
    }

    /**
     * Returns the LSP hover feature.
     *
     * @return the LSP hover feature.
     */
    @NotNull
    public final LSPHoverFeature getHoverFeature() {
        if (hoverFeature == null) {
            setHoverFeature(new LSPHoverFeature());
        }
        return hoverFeature;
    }

    /**
     * Initialize the LSP hover feature.
     *
     * @param hoverFeature the LSP hover feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setHoverFeature(@NotNull LSPHoverFeature hoverFeature) {
        hoverFeature.setClientFeatures(this);
        this.hoverFeature = hoverFeature;
        return this;
    }

    /**
     * Returns the LSP references feature.
     *
     * @return the LSP references feature.
     */
    @NotNull
    public final LSPReferencesFeature getReferencesFeature() {
        if (referencesFeature == null) {
            setReferencesFeature(new LSPReferencesFeature());
        }
        return referencesFeature;
    }

    /**
     * Initialize the LSP references feature.
     *
     * @param referencesFeature the LSP references feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setReferencesFeature(@NotNull LSPReferencesFeature referencesFeature) {
        referencesFeature.setClientFeatures(this);
        this.referencesFeature = referencesFeature;
        return this;
    }

    /**
     * Returns the LSP rename feature.
     *
     * @return the LSP rename feature.
     */
    @NotNull
    public final LSPRenameFeature getRenameFeature() {
        if (renameFeature == null) {
            setRenameFeature(new LSPRenameFeature());
        }
        return renameFeature;
    }

    /**
     * Initialize the LSP rename feature.
     *
     * @param renameFeature the LSP rename feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setRenameFeature(@NotNull LSPRenameFeature renameFeature) {
        renameFeature.setClientFeatures(this);
        this.renameFeature = renameFeature;
        return this;
    }

    /**
     * Returns the LSP semanticTokens feature.
     *
     * @return the LSP semanticTokens feature.
     */
    @NotNull
    public final LSPSemanticTokensFeature getSemanticTokensFeature() {
        if (semanticTokensFeature == null) {
            setSemanticTokensFeature(new LSPSemanticTokensFeature());
        }
        return semanticTokensFeature;
    }

    /**
     * Initialize the LSP semanticTokens feature.
     *
     * @param semanticTokensFeature the LSP semanticTokens feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setSemanticTokensFeature(@NotNull LSPSemanticTokensFeature semanticTokensFeature) {
        semanticTokensFeature.setClientFeatures(this);
        this.semanticTokensFeature = semanticTokensFeature;
        return this;
    }

    /**
     * Returns the LSP signatureHelp feature.
     *
     * @return the LSP signatureHelp feature.
     */
    @NotNull
    public final LSPSignatureHelpFeature getSignatureHelpFeature() {
        if (signatureHelpFeature == null) {
            setSignatureHelpFeature(new LSPSignatureHelpFeature());
        }
        return signatureHelpFeature;
    }

    /**
     * Initialize the LSP signatureHelp feature.
     *
     * @param signatureHelpFeature the LSP signatureHelp feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setSignatureHelpFeature(@NotNull LSPSignatureHelpFeature signatureHelpFeature) {
        signatureHelpFeature.setClientFeatures(this);
        this.signatureHelpFeature = signatureHelpFeature;
        return this;
    }

    /**
     * Returns the LSP typeDefinition feature.
     *
     * @return the LSP typeDefinition feature.
     */
    @NotNull
    public final LSPTypeDefinitionFeature getTypeDefinitionFeature() {
        if (typeDefinitionFeature == null) {
            setTypeDefinitionFeature(new LSPTypeDefinitionFeature());
        }
        return typeDefinitionFeature;
    }

    /**
     * Initialize the LSP typeDefinition feature.
     *
     * @param typeDefinitionFeature the LSP typeDefinition feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setTypeDefinitionFeature(@NotNull LSPTypeDefinitionFeature typeDefinitionFeature) {
        typeDefinitionFeature.setClientFeatures(this);
        this.typeDefinitionFeature = typeDefinitionFeature;
        return this;
    }

    /**
     * Returns the LSP usage feature.
     *
     * @return the LSP usage feature.
     */
    @NotNull
    public final LSPUsageFeature getUsageFeature() {
        if (usageFeature == null) {
            setUsageFeature(new LSPUsageFeature());
        }
        return usageFeature;
    }

    /**
     * Initialize the LSP usage feature.
     *
     * @param usageFeature the LSP usage feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setUsageFeature(@NotNull LSPUsageFeature usageFeature) {
        usageFeature.setClientFeatures(this);
        this.usageFeature = usageFeature;
        return this;
    }

    /**
     * Returns the LSP workspaceSymbol feature.
     *
     * @return the LSP workspaceSymbol feature.
     */
    @NotNull
    public final LSPWorkspaceSymbolFeature getWorkspaceSymbolFeature() {
        if (workspaceSymbolFeature == null) {
            setWorkspaceSymbolFeature(new LSPWorkspaceSymbolFeature());
        }
        return workspaceSymbolFeature;
    }

    /**
     * Initialize the LSP workspaceSymbol feature.
     *
     * @param workspaceSymbolFeature the LSP workspaceSymbol feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setWorkspaceSymbolFeature(@NotNull LSPWorkspaceSymbolFeature workspaceSymbolFeature) {
        workspaceSymbolFeature.setClientFeatures(this);
        this.workspaceSymbolFeature = workspaceSymbolFeature;
        return this;
    }

    /**
     * Set the language server wrapper.
     *
     * @param serverWrapper the language server wrapper.
     */
    @ApiStatus.Internal
    public final void setServerWrapper(LanguageServerWrapper serverWrapper) {
        this.serverWrapper = serverWrapper;
    }

    /**
     * Returns the language server wrapper.
     *
     * @return the language server wrapper.
     */
    @ApiStatus.Internal
    final LanguageServerWrapper getServerWrapper() {
        return serverWrapper;
    }

    /**
     * Dispose client features called when the language server is disposed.
     */
    @Override
    public void dispose() {
        serverWrapper = null;
        if (codeActionFeature != null) {
            codeActionFeature.dispose();
        }
        if (codeLensFeature != null) {
            codeLensFeature.dispose();
        }
        if (documentColorFeature != null) {
            documentColorFeature.dispose();
        }
        if (completionFeature != null) {
            completionFeature.dispose();
        }
        if (codeActionFeature != null) {
            codeActionFeature.dispose();
        }
        if (codeActionFeature != null) {
            codeActionFeature.dispose();
        }
        if (declarationFeature != null) {
            declarationFeature.dispose();
        }
        if (definitionFeature != null) {
            definitionFeature.dispose();
        }
        if (documentLinkFeature != null) {
            documentLinkFeature.dispose();
        }
        if (documentHighlightFeature != null) {
            documentHighlightFeature.dispose();
        }
        if (documentSymbolFeature != null) {
            documentSymbolFeature.dispose();
        }
        if (diagnosticFeature != null) {
            diagnosticFeature.dispose();
        }
        if (foldingRangeFeature != null) {
            foldingRangeFeature.dispose();
        }
        if (formattingFeature != null) {
            formattingFeature.dispose();
        }
        if (implementationFeature != null) {
            implementationFeature.dispose();
        }
        if (inlayHintFeature != null) {
            inlayHintFeature.dispose();
        }
        if (hoverFeature != null) {
            hoverFeature.dispose();
        }
        if (referencesFeature != null) {
            referencesFeature.dispose();
        }
        if (renameFeature != null) {
            renameFeature.dispose();
        }
        if (semanticTokensFeature != null) {
            semanticTokensFeature.dispose();
        }
        if (signatureHelpFeature != null) {
            signatureHelpFeature.dispose();
        }
        if (typeDefinitionFeature != null) {
            typeDefinitionFeature.dispose();
        }
        if (usageFeature != null) {
            usageFeature.dispose();
        }
        if (workspaceSymbolFeature != null) {
            workspaceSymbolFeature.dispose();
        }
    }

    public void setServerCapabilities(@NotNull ServerCapabilities serverCapabilities) {
        if (codeActionFeature != null) {
            codeActionFeature.setServerCapabilities(serverCapabilities);
        }
        if (codeLensFeature != null) {
            codeLensFeature.setServerCapabilities(serverCapabilities);
        }
        if (completionFeature != null) {
            completionFeature.setServerCapabilities(serverCapabilities);
        }
        if (codeActionFeature != null) {
            codeActionFeature.setServerCapabilities(serverCapabilities);
        }
        if (codeActionFeature != null) {
            codeActionFeature.setServerCapabilities(serverCapabilities);
        }
        if (declarationFeature != null) {
            declarationFeature.setServerCapabilities(serverCapabilities);
        }
        if (definitionFeature != null) {
            definitionFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentColorFeature != null) {
            documentColorFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentLinkFeature != null) {
            documentLinkFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentHighlightFeature != null) {
            documentHighlightFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentSymbolFeature != null) {
            documentSymbolFeature.setServerCapabilities(serverCapabilities);
        }
        if (diagnosticFeature != null) {
            diagnosticFeature.setServerCapabilities(serverCapabilities);
        }
        if (foldingRangeFeature != null) {
            foldingRangeFeature.setServerCapabilities(serverCapabilities);
        }
        if (formattingFeature != null) {
            formattingFeature.setServerCapabilities(serverCapabilities);
        }
        if (implementationFeature != null) {
            implementationFeature.setServerCapabilities(serverCapabilities);
        }
        if (inlayHintFeature != null) {
            inlayHintFeature.setServerCapabilities(serverCapabilities);
        }
        if (hoverFeature != null) {
            hoverFeature.setServerCapabilities(serverCapabilities);
        }
        if (referencesFeature != null) {
            referencesFeature.setServerCapabilities(serverCapabilities);
        }
        if (renameFeature != null) {
            renameFeature.setServerCapabilities(serverCapabilities);
        }
        if (semanticTokensFeature != null) {
            semanticTokensFeature.setServerCapabilities(serverCapabilities);
        }
        if (signatureHelpFeature != null) {
            signatureHelpFeature.setServerCapabilities(serverCapabilities);
        }
        if (typeDefinitionFeature != null) {
            typeDefinitionFeature.setServerCapabilities(serverCapabilities);
        }
        if (usageFeature != null) {
            usageFeature.setServerCapabilities(serverCapabilities);
        }
        if (workspaceSymbolFeature != null) {
            workspaceSymbolFeature.setServerCapabilities(serverCapabilities);
        }
    }

    @Nullable
    public TextDocumentServerCapabilityRegistry<? extends TextDocumentRegistrationOptions> getCapabilityRegistry(String method) {
        if (LSPRequestConstants.TEXT_DOCUMENT_CODE_ACTION.equals(method)) {
            // register 'textDocument/codeAction' capability
            return getCodeActionFeature().getCodeActionCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_CODE_LENS.equals(method)) {
            // register 'textDocument/codeLens' capability
            return getCodeLensFeature().getCodeLensCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_COLOR.equals(method)) {
            // register 'textDocument/documentColor' capability
            return getDocumentColorFeature().getDocumentColorCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_COMPLETION.equals(method)) {
            // register 'textDocument/completion' capability
            return getCompletionFeature().getCompletionCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DECLARATION.equals(method)) {
            // register 'textDocument/declaration' capability
            return getDeclarationFeature().getDeclarationCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DEFINITION.equals(method)) {
            // register 'textDocument/definition' capability
            return getDefinitionFeature().getDefinitionCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_HIGHLIGHT.equals(method)) {
            // register 'textDocument/documentHighlight' capability
            return getDocumentHighlightFeature().getDocumentHighlightCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_LINK.equals(method)) {
            // register 'textDocument/documentHighLink' capability
            return getDocumentLinkFeature().getDocumentLinkCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_SYMBOL.equals(method)) {
            // register 'textDocument/documentSymbol' capability
            return getDocumentSymbolFeature().getDocumentSymbolCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_FOLDING_RANGE.equals(method)) {
            // register 'textDocument/foldingRange' capability
            return getFoldingRangeFeature().getFoldingRangeCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_FORMATTING.equals(method)) {
            // register 'textDocument/formatting' capability
            return getFormattingFeature().getFormattingCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING.equals(method)) {
            // register 'textDocument/rangeFormatting' capability
            return getFormattingFeature().getRangeFormattingCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_HOVER.equals(method)) {
            // register 'textDocument/hover' capability
            return getHoverFeature().getHoverCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION.equals(method)) {
            // register 'textDocument/implementation' capability
            return getImplementationFeature().getImplementationCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_INLAY_HINT.equals(method)) {
            // register 'textDocument/inlayHint' capability
            return getInlayHintFeature().getInlayHintCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_REFERENCES.equals(method)) {
            // register 'textDocument/references' capability
            return getReferencesFeature().getReferencesCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_RENAME.equals(method)) {
            // register 'textDocument/rename' capability
            return getRenameFeature().getRenameCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_SIGNATURE_HELP.equals(method)) {
            // register 'textDocument/signatureHelp' capability
            return getSignatureHelpFeature().getSignatureHelpCapabilityRegistry();
        }
        if (LSPRequestConstants.TEXT_DOCUMENT_TYPE_DEFINITION.equals(method)) {
            // register 'textDocument/typeDefinition' capability
            return getTypeDefinitionFeature().getTypeDefinitionCapabilityRegistry();
        }
        return null;
    }

}

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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.ServerStatus;
import com.redhat.devtools.lsp4ij.server.capabilities.TextDocumentServerCapabilityRegistry;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * LSP  client features.
 */
@ApiStatus.Experimental
public class LSPClientFeatures implements Disposable, FileUriSupport {

    private LanguageServerWrapper serverWrapper;

    private LSPCallHierarchyFeature callHierarchyFeature;

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

    private LSPSelectionRangeFeature selectionRangeFeature;

    private LSPFormattingFeature formattingFeature;

    private LSPOnTypeFormattingFeature onTypeFormattingFeature;

    private LSPHoverFeature hoverFeature;

    private LSPImplementationFeature implementationFeature;

    private LSPInlayHintFeature inlayHintFeature;

    private LSPProgressFeature progressFeature;
    
    private LSPReferencesFeature referencesFeature;

    private LSPRenameFeature renameFeature;

    private LSPSemanticTokensFeature semanticTokensFeature;

    private LSPSignatureHelpFeature signatureHelpFeature;

    private LSPTypeDefinitionFeature typeDefinitionFeature;

    private LSPTypeHierarchyFeature typeHierarchyFeature;

    private LSPUsageFeature usageFeature;

    private LSPWorkspaceSymbolFeature workspaceSymbolFeature;

    /**
     * Returns true if the language server is enabled for the given file and false otherwise. Default to true
     *
     * @param file the file for test
     * @return true if the language server is enabled for the input and false otherwise. Default to true
     */
    public boolean isEnabled(@NotNull VirtualFile file) {
        return true;
    }

    /**
     * This method is invoked just before {@link LanguageServer#initialize(InitializeParams)}
     * to enable customization of the language server's initialization parameters
     * (e.g., {@link InitializeParams#getWorkDoneToken()}).
     *
     * @param initializeParams the initialize parameters.
     */
    public void initializeParams(@NotNull InitializeParams initializeParams) {
    }

    /**
     * Overrides this method if you need to generate custom URI.
     *
     * @param file the virtual file.
     *
     * @return the file Uri and null otherwise.
     */
    @Override
    public @Nullable URI getFileUri(@NotNull VirtualFile file) {
        return null;
    }

    /**
     * Overrides this method if you need to support custom file uri.
     *
     * @param fileUri the file Uri.
     * @return the virtual file and null otherwise.
     */
    @Override
    public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
        return null;
    }

    /**
     * Determines whether or not the language grammar for the file is case-sensitive.
     *
     * @param file the file
     * @return true if the file's language grammar is case-sensitive; otherwise false
     */
    public boolean isCaseSensitive(@NotNull PsiFile file) {
        // Default to case-insensitive
        return false;
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
     * Returns the LSP callHierarchy feature.
     *
     * @return the LSP callHierarchy feature.
     */
    @NotNull
    public final LSPCallHierarchyFeature getCallHierarchyFeature() {
        if (callHierarchyFeature == null) {
            initCallHierarchyFeature();
        }
        return callHierarchyFeature;
    }

    private synchronized void initCallHierarchyFeature() {
        if (callHierarchyFeature != null) {
            return;
        }
        setCallHierarchyFeature(new LSPCallHierarchyFeature());
    }

    /**
     * Initialize the LSP callHierarchy feature.
     *
     * @param callHierarchyFeature the LSP callHierarchy feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setCallHierarchyFeature(@NotNull LSPCallHierarchyFeature callHierarchyFeature) {
        callHierarchyFeature.setClientFeatures(this);
        this.callHierarchyFeature = callHierarchyFeature;
        return this;
    }

    /**
     * Returns the LSP codeAction feature.
     *
     * @return the LSP codeAction feature.
     */
    @NotNull
    public final LSPCodeActionFeature getCodeActionFeature() {
        if (codeActionFeature == null) {
            initCodeActionFeature();
        }
        return codeActionFeature;
    }

    private synchronized void initCodeActionFeature() {
        if (codeActionFeature != null) {
            return;
        }
        setCodeActionFeature(new LSPCodeActionFeature());
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
            initCodeLensFeature();
        }
        return codeLensFeature;
    }

    private synchronized void initCodeLensFeature() {
        if (codeLensFeature != null) {
            return;
        }
        setCodeLensFeature(new LSPCodeLensFeature());
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
            initDocumentColorFeature();
        }
        return documentColorFeature;
    }

    private synchronized void initDocumentColorFeature() {
        if (documentColorFeature != null) {
            return;
        }
        setDocumentColorFeature(new LSPDocumentColorFeature());
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
            initCompletionFeature();
        }
        return completionFeature;
    }

    private synchronized void initCompletionFeature() {
        if (completionFeature != null) {
            return;
        }
        setCompletionFeature(new LSPCompletionFeature());
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
            initDeclarationFeature();
        }
        return declarationFeature;
    }

    private synchronized void initDeclarationFeature() {
        if (declarationFeature != null) {
            return;
        }
        setDeclarationFeature(new LSPDeclarationFeature());
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
            initDefinitionFeature();
        }
        return definitionFeature;
    }

    private synchronized void initDefinitionFeature() {
        if (definitionFeature != null) {
            return;
        }
        setDefinitionFeature(new LSPDefinitionFeature());
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
            initDocumentHighlightFeature();
        }
        return documentHighlightFeature;
    }

    private synchronized void initDocumentHighlightFeature() {
        if (documentHighlightFeature != null) {
            return;
        }
        setDocumentHighlightFeature(new LSPDocumentHighlightFeature());
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
            initDocumentLinkFeature();
        }
        return documentLinkFeature;
    }

    private synchronized void initDocumentLinkFeature() {
        if (documentLinkFeature != null) {
            return;
        }
        setDocumentLinkFeature(new LSPDocumentLinkFeature());
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
            initDocumentSymbolFeature();
        }
        return documentSymbolFeature;
    }

    private synchronized void initDocumentSymbolFeature() {
        if (documentSymbolFeature != null) {
            return;
        }
        setDocumentSymbolFeature(new LSPDocumentSymbolFeature());
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
            initDiagnosticFeature();
        }
        return diagnosticFeature;
    }

    private synchronized void initDiagnosticFeature() {
        if (diagnosticFeature != null) {
            return;
        }
        setDiagnosticFeature(new LSPDiagnosticFeature());
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
            initFoldingRangeFeature();
        }
        return foldingRangeFeature;
    }

    private synchronized void initFoldingRangeFeature() {
        if (foldingRangeFeature != null) {
            return;
        }
        setFoldingRangeFeature(new LSPFoldingRangeFeature());
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
     * Returns the LSP selectionRange feature.
     *
     * @return the LSP selectionRange feature.
     */
    @NotNull
    public final LSPSelectionRangeFeature getSelectionRangeFeature() {
        if (selectionRangeFeature == null) {
            initSelectionRangeFeature();
        }
        return selectionRangeFeature;
    }

    private synchronized void initSelectionRangeFeature() {
        if (selectionRangeFeature != null) {
            return;
        }
        setSelectionRangeFeature(new LSPSelectionRangeFeature());
    }

    /**
     * Initialize the LSP selectionRange feature.
     *
     * @param selectionRangeFeature the LSP selectionRange feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setSelectionRangeFeature(@NotNull LSPSelectionRangeFeature selectionRangeFeature) {
        selectionRangeFeature.setClientFeatures(this);
        this.selectionRangeFeature = selectionRangeFeature;
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
            initFormattingFeature();
        }
        return formattingFeature;
    }

    private synchronized void initFormattingFeature() {
        if (formattingFeature != null) {
            return;
        }
        setFormattingFeature(new LSPFormattingFeature());
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
     * Returns the LSP on-type formatting feature.
     *
     * @return the LSP on-type formatting feature.
     */
    @NotNull
    public final LSPOnTypeFormattingFeature getOnTypeFormattingFeature() {
        if (onTypeFormattingFeature == null) {
            initOnTypeFormattingFeature();
        }
        return onTypeFormattingFeature;
    }

    private synchronized void initOnTypeFormattingFeature() {
        if (onTypeFormattingFeature != null) {
            return;
        }
        setOnTypeFormattingFeature(new LSPOnTypeFormattingFeature());
    }

    /**
     * Initialize the LSP formatting feature.
     *
     * @param onTypeFormattingFeature the LSP formatting feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setOnTypeFormattingFeature(@NotNull LSPOnTypeFormattingFeature onTypeFormattingFeature) {
        onTypeFormattingFeature.setClientFeatures(this);
        this.onTypeFormattingFeature = onTypeFormattingFeature;
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
            initHoverFeature();
        }
        return hoverFeature;
    }

    private synchronized void initHoverFeature() {
        if (hoverFeature != null) {
            return;
        }
        setHoverFeature(new LSPHoverFeature());
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
     * Returns the LSP implementation feature.
     *
     * @return the LSP implementation feature.
     */
    @NotNull
    public final LSPImplementationFeature getImplementationFeature() {
        if (implementationFeature == null) {
            initImplementationFeature();
        }
        return implementationFeature;
    }

    private synchronized void initImplementationFeature() {
        if (implementationFeature != null) {
            return;
        }
        setImplementationFeature(new LSPImplementationFeature());
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
            initInlayHintFeature();
        }
        return inlayHintFeature;
    }

    private synchronized void initInlayHintFeature() {
        if (inlayHintFeature != null) {
            return;
        }
        setInlayHintFeature(new LSPInlayHintFeature());
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
     * Returns the LSP progress feature.
     *
     * @return the LSP progress feature.
     */
    @NotNull
    public final LSPProgressFeature getProgressFeature() {
        if (progressFeature == null) {
            initProgressFeature();
        }
        return progressFeature;
    }

    private synchronized void initProgressFeature() {
        if (progressFeature != null) {
            return;
        }
        setProgressFeature(new LSPProgressFeature());
    }

    /**
     * Initialize the LSP progress feature.
     *
     * @param progressFeature the LSP progress feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setProgressFeature(@NotNull LSPProgressFeature progressFeature) {
        progressFeature.setClientFeatures(this);
        this.progressFeature = progressFeature;
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
            initReferencesFeature();
        }
        return referencesFeature;
    }

    private synchronized void initReferencesFeature() {
        if (referencesFeature != null) {
            return;
        }
        setReferencesFeature(new LSPReferencesFeature());
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
            initRenameFeature();
        }
        return renameFeature;
    }

    private synchronized void initRenameFeature() {
        if (renameFeature != null) {
            return;
        }
        setRenameFeature(new LSPRenameFeature());
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
            initSemanticTokensFeature();
        }
        return semanticTokensFeature;
    }

    private synchronized void initSemanticTokensFeature() {
        if (semanticTokensFeature != null) {
            return;
        }
        setSemanticTokensFeature(new LSPSemanticTokensFeature());
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
            initSignatureHelpFeature();
        }
        return signatureHelpFeature;
    }

    private synchronized void initSignatureHelpFeature() {
        if (signatureHelpFeature != null) {
            return;
        }
        setSignatureHelpFeature(new LSPSignatureHelpFeature());
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
            initTypeDefinitionFeature();
        }
        return typeDefinitionFeature;
    }

    private synchronized void initTypeDefinitionFeature() {
        if (typeDefinitionFeature != null) {
            return;
        }
        setTypeDefinitionFeature(new LSPTypeDefinitionFeature());
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
     * Returns the LSP typeHierarchy feature.
     *
     * @return the LSP typeHierarchy feature.
     */
    @NotNull
    public final LSPTypeHierarchyFeature getTypeHierarchyFeature() {
        if (typeHierarchyFeature == null) {
            initTypeHierarchyFeature();
        }
        return typeHierarchyFeature;
    }

    private synchronized void initTypeHierarchyFeature() {
        if (typeHierarchyFeature != null) {
            return;
        }
        setTypeHierarchyFeature(new LSPTypeHierarchyFeature());
    }

    /**
     * Initialize the LSP typeHierarchy feature.
     *
     * @param typeHierarchyFeature the LSP typeHierarchy feature.
     * @return the LSP client features.
     */
    public final LSPClientFeatures setTypeHierarchyFeature(@NotNull LSPTypeHierarchyFeature typeHierarchyFeature) {
        typeHierarchyFeature.setClientFeatures(this);
        this.typeHierarchyFeature = typeHierarchyFeature;
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
            initUsageFeature();
        }
        return usageFeature;
    }

    private synchronized void initUsageFeature() {
        if (usageFeature != null) {
            return;
        }
        setUsageFeature(new LSPUsageFeature());
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
            initWorkspaceSymbolFeature();
        }
        return workspaceSymbolFeature;
    }

    private synchronized void initWorkspaceSymbolFeature() {
        if (workspaceSymbolFeature != null) {
            return;
        }
        setWorkspaceSymbolFeature(new LSPWorkspaceSymbolFeature());
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
    public final LanguageServerWrapper getServerWrapper() {
        return serverWrapper;
    }

    /**
     * Dispose client features called when the language server is disposed.
     */
    @Override
    public void dispose() {
        if (callHierarchyFeature != null) {
            callHierarchyFeature.dispose();
        }
        if (codeActionFeature != null) {
            codeActionFeature.dispose();
        }
        if (codeLensFeature != null) {
            codeLensFeature.dispose();
        }
        if (completionFeature != null) {
            completionFeature.dispose();
        }
        if (declarationFeature != null) {
            declarationFeature.dispose();
        }
        if (definitionFeature != null) {
            definitionFeature.dispose();
        }
        if (diagnosticFeature != null) {
            diagnosticFeature.dispose();
        }
        if (documentColorFeature != null) {
            documentColorFeature.dispose();
        }
        if (documentHighlightFeature != null) {
            documentHighlightFeature.dispose();
        }
        if (documentLinkFeature != null) {
            documentLinkFeature.dispose();
        }
        if (documentSymbolFeature != null) {
            documentSymbolFeature.dispose();
        }
        if (foldingRangeFeature != null) {
            foldingRangeFeature.dispose();
        }
        if (formattingFeature != null) {
            formattingFeature.dispose();
        }
        if (hoverFeature != null) {
            hoverFeature.dispose();
        }
        if (implementationFeature != null) {
            implementationFeature.dispose();
        }
        if (inlayHintFeature != null) {
            inlayHintFeature.dispose();
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
        if (typeHierarchyFeature != null) {
            typeHierarchyFeature.dispose();
        }
        if (usageFeature != null) {
            usageFeature.dispose();
        }
        if (workspaceSymbolFeature != null) {
            workspaceSymbolFeature.dispose();
        }
    }

    public void setServerCapabilities(@NotNull ServerCapabilities serverCapabilities) {
        if (callHierarchyFeature != null) {
            callHierarchyFeature.setServerCapabilities(serverCapabilities);
        }
        if (codeActionFeature != null) {
            codeActionFeature.setServerCapabilities(serverCapabilities);
        }
        if (codeLensFeature != null) {
            codeLensFeature.setServerCapabilities(serverCapabilities);
        }
        if (completionFeature != null) {
            completionFeature.setServerCapabilities(serverCapabilities);
        }
        if (declarationFeature != null) {
            declarationFeature.setServerCapabilities(serverCapabilities);
        }
        if (definitionFeature != null) {
            definitionFeature.setServerCapabilities(serverCapabilities);
        }
        if (diagnosticFeature != null) {
            diagnosticFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentColorFeature != null) {
            documentColorFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentHighlightFeature != null) {
            documentHighlightFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentLinkFeature != null) {
            documentLinkFeature.setServerCapabilities(serverCapabilities);
        }
        if (documentSymbolFeature != null) {
            documentSymbolFeature.setServerCapabilities(serverCapabilities);
        }
        if (foldingRangeFeature != null) {
            foldingRangeFeature.setServerCapabilities(serverCapabilities);
        }
        if (formattingFeature != null) {
            formattingFeature.setServerCapabilities(serverCapabilities);
        }
        if (hoverFeature != null) {
            hoverFeature.setServerCapabilities(serverCapabilities);
        }
        if (implementationFeature != null) {
            implementationFeature.setServerCapabilities(serverCapabilities);
        }
        if (inlayHintFeature != null) {
            inlayHintFeature.setServerCapabilities(serverCapabilities);
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
        if (typeHierarchyFeature != null) {
            typeHierarchyFeature.setServerCapabilities(serverCapabilities);
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
        if (method == null) {
            return null;
        }

        return switch (method) {
            // register 'textDocument/codeAction' capability
            case LSPRequestConstants.TEXT_DOCUMENT_CODE_ACTION ->
                    getCodeActionFeature().getCodeActionCapabilityRegistry();
            // register 'textDocument/codeLens' capability
            case LSPRequestConstants.TEXT_DOCUMENT_CODE_LENS -> getCodeLensFeature().getCodeLensCapabilityRegistry();
            // register 'textDocument/documentColor' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_COLOR ->
                    getDocumentColorFeature().getDocumentColorCapabilityRegistry();
            // register 'textDocument/completion' capability
            case LSPRequestConstants.TEXT_DOCUMENT_COMPLETION ->
                    getCompletionFeature().getCompletionCapabilityRegistry();
            // register 'textDocument/declaration' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DECLARATION ->
                    getDeclarationFeature().getDeclarationCapabilityRegistry();
            // register 'textDocument/definition' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DEFINITION ->
                    getDefinitionFeature().getDefinitionCapabilityRegistry();
            // register 'textDocument/documentHighlight' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_HIGHLIGHT ->
                    getDocumentHighlightFeature().getDocumentHighlightCapabilityRegistry();
            // register 'textDocument/documentHighLink' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_LINK ->
                    getDocumentLinkFeature().getDocumentLinkCapabilityRegistry();
            // register 'textDocument/documentSymbol' capability
            case LSPRequestConstants.TEXT_DOCUMENT_DOCUMENT_SYMBOL ->
                    getDocumentSymbolFeature().getDocumentSymbolCapabilityRegistry();
            // register 'textDocument/foldingRange' capability
            case LSPRequestConstants.TEXT_DOCUMENT_FOLDING_RANGE ->
                    getFoldingRangeFeature().getFoldingRangeCapabilityRegistry();
            // register 'textDocument/selectionRange' capability
            case LSPRequestConstants.TEXT_DOCUMENT_SELECTION_RANGE ->
                    getSelectionRangeFeature().getSelectionRangeCapabilityRegistry();
            // register 'textDocument/formatting' capability
            case LSPRequestConstants.TEXT_DOCUMENT_FORMATTING ->
                    getFormattingFeature().getFormattingCapabilityRegistry();
            // register 'textDocument/rangeFormatting' capability
            case LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING ->
                    getFormattingFeature().getRangeFormattingCapabilityRegistry();
            // register 'textDocument/hover' capability
            case LSPRequestConstants.TEXT_DOCUMENT_HOVER -> getHoverFeature().getHoverCapabilityRegistry();
            // register 'textDocument/implementation' capability
            case LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION ->
                    getImplementationFeature().getImplementationCapabilityRegistry();
            // register 'textDocument/inlayHint' capability
            case LSPRequestConstants.TEXT_DOCUMENT_INLAY_HINT -> getInlayHintFeature().getInlayHintCapabilityRegistry();
            // register 'textDocument/callHierarchy' capability
            case LSPRequestConstants.TEXT_DOCUMENT_CALL_HIERARCHY ->
                    getCallHierarchyFeature().getCallHierarchyCapabilityRegistry();
            // register 'textDocument/references' capability
            case LSPRequestConstants.TEXT_DOCUMENT_REFERENCES ->
                    getReferencesFeature().getReferencesCapabilityRegistry();
            // register 'textDocument/rename' capability
            case LSPRequestConstants.TEXT_DOCUMENT_RENAME -> getRenameFeature().getRenameCapabilityRegistry();
            // register 'textDocument/signatureHelp' capability
            case LSPRequestConstants.TEXT_DOCUMENT_SIGNATURE_HELP ->
                    getSignatureHelpFeature().getSignatureHelpCapabilityRegistry();
            // register 'textDocument/typeDefinition' capability
            case LSPRequestConstants.TEXT_DOCUMENT_TYPE_DEFINITION ->
                    getTypeDefinitionFeature().getTypeDefinitionCapabilityRegistry();
            // register 'textDocument/typeHierarchy' capability
            case LSPRequestConstants.TEXT_DOCUMENT_TYPE_HIERARCHY ->
                    getTypeHierarchyFeature().getTypeHierarchyCapabilityRegistry();
            default -> null;
        };
    }

}

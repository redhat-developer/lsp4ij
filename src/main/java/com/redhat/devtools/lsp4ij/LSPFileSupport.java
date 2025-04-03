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
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.client.CoalesceByKey;
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyIncomingCallsSupport;
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPCallHierarchyOutgoingCallsSupport;
import com.redhat.devtools.lsp4ij.features.callHierarchy.LSPPrepareCallHierarchySupport;
import com.redhat.devtools.lsp4ij.features.codeAction.intention.LSPIntentionCodeActionSupport;
import com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensSupport;
import com.redhat.devtools.lsp4ij.features.color.LSPColorSupport;
import com.redhat.devtools.lsp4ij.features.completion.LSPCompletionSupport;
import com.redhat.devtools.lsp4ij.features.declaration.LSPDeclarationSupport;
import com.redhat.devtools.lsp4ij.features.documentLink.LSPDocumentLinkSupport;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolSupport;
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverSupport;
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeSupport;
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingSupport;
import com.redhat.devtools.lsp4ij.features.formatting.LSPOnTypeFormattingSupport;
import com.redhat.devtools.lsp4ij.features.highlight.LSPHighlightSupport;
import com.redhat.devtools.lsp4ij.features.implementation.LSPImplementationSupport;
import com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsSupport;
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionSupport;
import com.redhat.devtools.lsp4ij.features.references.LSPReferenceSupport;
import com.redhat.devtools.lsp4ij.features.rename.LSPPrepareRenameSupport;
import com.redhat.devtools.lsp4ij.features.rename.LSPRenameSupport;
import com.redhat.devtools.lsp4ij.features.selectionRange.LSPSelectionRangeSupport;
import com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokensSupport;
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelpSupport;
import com.redhat.devtools.lsp4ij.features.typeDefinition.LSPTypeDefinitionSupport;
import com.redhat.devtools.lsp4ij.features.typeHierarchy.LSPPrepareTypeHierarchySupport;
import com.redhat.devtools.lsp4ij.features.typeHierarchy.LSPTypeHierarchySubtypesSupport;
import com.redhat.devtools.lsp4ij.features.typeHierarchy.LSPTypeHierarchySupertypesSupport;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * LSP file support stored in the opened {@link PsiFile} with key "lsp.file.support"
 * which manages and caches LSP textDocument requests like codeLens, inlayHint, color futures, etc.
 */
@ApiStatus.Internal
public class LSPFileSupport extends UserDataHolderBase implements Disposable {

    private static final Key<LSPFileSupport> LSP_FILE_SUPPORT_KEY = Key.create("lsp.file.support");
    public static final @NotNull CancelChecker NO_CANCELLABLE_CHECKER = () -> {
    };

    private volatile Alarm refreshPsiFileAlarm = null;

    private final PsiFile file;

    private final LSPCodeLensSupport codeLensSupport;

    private final LSPInlayHintsSupport inlayHintsSupport;

    private final LSPColorSupport colorSupport;

    private final LSPFoldingRangeSupport foldingRangeSupport;

    private final LSPSelectionRangeSupport selectionRangeSupport;

    private final LSPFormattingSupport formattingSupport;

    private final LSPOnTypeFormattingSupport onTypeFormattingSupport;

    private final LSPHighlightSupport highlightSupport;

    private final LSPSignatureHelpSupport signatureHelpSupport;

    private final LSPDocumentLinkSupport documentLinkSupport;

    private final LSPHoverSupport hoverSupport;

    private final LSPIntentionCodeActionSupport intentionCodeActionSupport;

    private final LSPPrepareRenameSupport prepareRenameSupport;

    private final LSPRenameSupport renameSupport;

    private final LSPCompletionSupport completionSupport;

    private final LSPImplementationSupport implementationSupport;

    private final LSPReferenceSupport referenceSupport;

    private final LSPDefinitionSupport definitionSupport;

    private final LSPDeclarationSupport declarationSupport;

    private final LSPTypeDefinitionSupport typeDefinitionSupport;

    private final LSPSemanticTokensSupport semanticTokensSupport;

    private final LSPDocumentSymbolSupport documentSymbolSupport;

    private final LSPPrepareCallHierarchySupport prepareCallHierarchySupport;
    private final LSPCallHierarchyIncomingCallsSupport callHierarchyIncomingCallsSupport;
    private final LSPCallHierarchyOutgoingCallsSupport callHierarchyOutgoingCallsSupport;

    private final LSPPrepareTypeHierarchySupport prepareTypeHierarchySupport;
    private final LSPTypeHierarchySubtypesSupport typeHierarchySubtypesSupport;
    private final LSPTypeHierarchySupertypesSupport typeHierarchySupertypesSupport;

    private LSPFileSupport(@NotNull PsiFile file) {
        this.file = file;
        this.codeLensSupport = new LSPCodeLensSupport(file);
        this.inlayHintsSupport = new LSPInlayHintsSupport(file);
        this.colorSupport = new LSPColorSupport(file);
        this.foldingRangeSupport = new LSPFoldingRangeSupport(file);
        this.selectionRangeSupport = new LSPSelectionRangeSupport(file);
        this.formattingSupport = new LSPFormattingSupport(file);
        this.onTypeFormattingSupport = new LSPOnTypeFormattingSupport(file);
        this.highlightSupport = new LSPHighlightSupport(file);
        this.signatureHelpSupport = new LSPSignatureHelpSupport(file);
        this.documentLinkSupport = new LSPDocumentLinkSupport(file);
        this.hoverSupport = new LSPHoverSupport(file);
        this.intentionCodeActionSupport = new LSPIntentionCodeActionSupport(file);
        this.prepareRenameSupport = new LSPPrepareRenameSupport(file);
        this.renameSupport = new LSPRenameSupport(file);
        this.completionSupport = new LSPCompletionSupport(file);
        this.implementationSupport = new LSPImplementationSupport(file);
        this.referenceSupport = new LSPReferenceSupport(file);
        this.definitionSupport = new LSPDefinitionSupport(file);
        this.declarationSupport = new LSPDeclarationSupport(file);
        this.typeDefinitionSupport = new LSPTypeDefinitionSupport(file);
        this.semanticTokensSupport = new LSPSemanticTokensSupport(file);
        this.documentSymbolSupport = new LSPDocumentSymbolSupport(file);
        this.prepareCallHierarchySupport = new LSPPrepareCallHierarchySupport(file);
        this.callHierarchyIncomingCallsSupport = new LSPCallHierarchyIncomingCallsSupport(file);
        this.callHierarchyOutgoingCallsSupport = new LSPCallHierarchyOutgoingCallsSupport(file);
        this.prepareTypeHierarchySupport = new LSPPrepareTypeHierarchySupport(file);
        this.typeHierarchySubtypesSupport = new LSPTypeHierarchySubtypesSupport(file);
        this.typeHierarchySupertypesSupport = new LSPTypeHierarchySupertypesSupport(file);
        file.putUserData(LSP_FILE_SUPPORT_KEY, this);
    }

    @Override
    public void dispose() {
        // cancel all LSP requests
        file.putUserData(LSP_FILE_SUPPORT_KEY, null);
        getCodeLensSupport().cancel();
        getInlayHintsSupport().cancel();
        getColorSupport().cancel();
        getFoldingRangeSupport().cancel();
        getSelectionRangeSupport().cancel();
        getFormattingSupport().cancel();
        getOnTypeFormattingSupport().cancel();
        getHighlightSupport().cancel();
        getSignatureHelpSupport().cancel();
        getDocumentLinkSupport().cancel();
        getHoverSupport().cancel();
        getIntentionCodeActionSupport().cancel();
        getPrepareRenameSupport().cancel();
        getRenameSupport().cancel();
        getCompletionSupport().cancel();
        getImplementationSupport().cancel();
        getReferenceSupport().cancel();
        getDeclarationSupport().cancel();
        getTypeDefinitionSupport().cancel();
        getSemanticTokensSupport().cancel();
        getDocumentSymbolSupport().cancel();
        getPrepareCallHierarchySupport().cancel();
        getCallHierarchyIncomingCallsSupport().cancel();
        getCallHierarchyOutgoingCallsSupport().cancel();
        getPrepareTypeHierarchySupport().cancel();
        getTypeHierarchySubtypesSupport().cancel();
        getTypeHierarchySupertypesSupport().cancel();
        var map = getUserMap();
        for (var key : map.getKeys()) {
            var value = map.get(key);
            if (value instanceof Disposable disposable) {
                disposable.dispose();
            }
        }
    }

    /**
     * Returns the LSP codeLens support.
     *
     * @return the LSP codeLens support.
     */
    public LSPCodeLensSupport getCodeLensSupport() {
        return codeLensSupport;
    }

    /**
     * Returns the LSP inlayHint support.
     *
     * @return the LSP inlayHint support.
     */
    public LSPInlayHintsSupport getInlayHintsSupport() {
        return inlayHintsSupport;
    }

    /**
     * Returns the LSP color support.
     *
     * @return the LSP color support.
     */
    public LSPColorSupport getColorSupport() {
        return colorSupport;
    }

    /**
     * Returns the LSP folding range support.
     *
     * @return the LSP folding range support.
     */
    public LSPFoldingRangeSupport getFoldingRangeSupport() {
        return foldingRangeSupport;
    }

    /**
     * Returns the LSP selection range support.
     *
     * @return the LSP selection range support.
     */
    public LSPSelectionRangeSupport getSelectionRangeSupport() {
        return selectionRangeSupport;
    }

    /**
     * Returns the LSP formatting support.
     *
     * @return the LSP formatting support.
     */
    public LSPFormattingSupport getFormattingSupport() {
        return formattingSupport;
    }

    /**
     * Returns the LSP on-type formatting support.
     *
     * @return the LSP on-type formatting support.
     */
    public LSPOnTypeFormattingSupport getOnTypeFormattingSupport() {
        return onTypeFormattingSupport;
    }

    /**
     * Returns the LSP highlight support.
     *
     * @return the LSP highlight support.
     */
    public LSPHighlightSupport getHighlightSupport() {
        return highlightSupport;
    }

    /**
     * Returns the LSP signature help support.
     *
     * @return the LSP signature help support.
     */
    public LSPSignatureHelpSupport getSignatureHelpSupport() {
        return signatureHelpSupport;
    }

    /**
     * Returns the LSP document link support.
     *
     * @return the LSP document link support.
     */
    public LSPDocumentLinkSupport getDocumentLinkSupport() {
        return documentLinkSupport;
    }

    /**
     * Returns the LSP hover support.
     *
     * @return the LSP hover support.
     */
    public LSPHoverSupport getHoverSupport() {
        return hoverSupport;
    }

    /**
     * Returns the LSP code action support.
     *
     * @return the LSP code action support.
     */
    public LSPIntentionCodeActionSupport getIntentionCodeActionSupport() {
        return intentionCodeActionSupport;
    }

    /**
     * Returns the LSP prepare rename support.
     *
     * @return the LSP prepare rename support.
     */
    public LSPPrepareRenameSupport getPrepareRenameSupport() {
        return prepareRenameSupport;
    }

    /**
     * Returns the LSP prepare rename support.
     *
     * @return the LSP prepare rename support.
     */
    public LSPRenameSupport getRenameSupport() {
        return renameSupport;
    }

    /**
     * Returns the LSP completion support.
     *
     * @return the LSP completion support.
     */
    public LSPCompletionSupport getCompletionSupport() {
        return completionSupport;
    }

    /**
     * Returns the LSP implementation support.
     *
     * @return the LSP implementation support.
     */
    public LSPImplementationSupport getImplementationSupport() {
        return implementationSupport;
    }

    /**
     * Returns the LSP reference support.
     *
     * @return the LSP reference support.
     */
    public LSPReferenceSupport getReferenceSupport() {
        return referenceSupport;
    }

    /**
     * Returns the LSP definition support.
     *
     * @return the LSP definition support.
     */
    public LSPDefinitionSupport getDefinitionSupport() {
        return definitionSupport;
    }

    /**
     * Returns the LSP declaration support.
     *
     * @return the LSP declaration support.
     */
    public LSPDeclarationSupport getDeclarationSupport() {
        return declarationSupport;
    }

    /**
     * Returns the LSP typeDefinition support.
     *
     * @return the LSP typeDefinition support.
     */
    public LSPTypeDefinitionSupport getTypeDefinitionSupport() {
        return typeDefinitionSupport;
    }

    /**
     * Returns the LSP semantic tokens support.
     *
     * @return the LSP semantic tokens support.
     */
    public LSPSemanticTokensSupport getSemanticTokensSupport() {
        return semanticTokensSupport;
    }

    /**
     * Returns the LSP document symbol support.
     *
     * @return the LSP document symbol support.
     */
    public LSPDocumentSymbolSupport getDocumentSymbolSupport() {
        return documentSymbolSupport;
    }

    /**
     * Returns the LSP prepare call hierarchy support.
     *
     * @return the LSP prepare call hierarchy support.
     */
    public LSPPrepareCallHierarchySupport getPrepareCallHierarchySupport() {
        return prepareCallHierarchySupport;
    }

    /**
     * Returns the LSP call hierarchy incoming calls support.
     *
     * @return the LSP call hierarchy incoming calls support.
     */
    public LSPCallHierarchyIncomingCallsSupport getCallHierarchyIncomingCallsSupport() {
        return callHierarchyIncomingCallsSupport;
    }

    /**
     * Returns the LSP prepare call hierarchy outgoing calls support.
     *
     * @return the LSP prepare call hierarchy outgoing calls support.
     */
    public LSPCallHierarchyOutgoingCallsSupport getCallHierarchyOutgoingCallsSupport() {
        return callHierarchyOutgoingCallsSupport;
    }

    /**
     * Returns the LSP prepare type hierarchy support.
     *
     * @return the LSP prepare type hierarchy support.
     */
    public LSPPrepareTypeHierarchySupport getPrepareTypeHierarchySupport() {
        return prepareTypeHierarchySupport;
    }

    /**
     * Returns the LSP type hierarchy subtypes support.
     *
     * @return the LSP type hierarchy subtypes support.
     */
    public LSPTypeHierarchySubtypesSupport getTypeHierarchySubtypesSupport() {
        return typeHierarchySubtypesSupport;
    }

    /**
     * Returns the LSP type hierarchy supertypes support.
     *
     * @return the LSP type hierarchy supertypes support.
     */
    public LSPTypeHierarchySupertypesSupport getTypeHierarchySupertypesSupport() {
        return typeHierarchySupertypesSupport;
    }

    /**
     * Call 'DaemonCodeAnalyzer.getInstance(project).restart(file);' with debounce.
     */
    public void restartDaemonCodeAnalyzerWithDebounce() {
        restartDaemonCodeAnalyzerWithDebounce(NO_CANCELLABLE_CHECKER);
    }

    /**
     * Call 'DaemonCodeAnalyzer.getInstance(project).restart(file);' with debounce by checking the given cancel checker.
     *
     * @param cancelChecker the cancel checker.
     */
    public void restartDaemonCodeAnalyzerWithDebounce(@NotNull CancelChecker cancelChecker) {
        if (cancelChecker.isCanceled()) {
            return;
        }
        var alarm = getRefreshPsiFileAlarm();
        alarm.cancelAllRequests();
        alarm.addRequest(() -> {
            var project = file.getProject();
            var coalesceBy = new CoalesceByKey("psiFile/refresh", file.getVirtualFile().getUrl());
            var executeInSmartMode = DumbService.getInstance(project).isDumb();
            var action = ReadAction.nonBlocking((Callable<Void>) () -> {
                        if (!cancelChecker.isCanceled()) {
                            DaemonCodeAnalyzer.getInstance(project).restart(file);
                        }
                        return null;
                    }).expireWith(this)
                    .coalesceBy(coalesceBy);
            if (executeInSmartMode) {
                action.inSmartMode(project);
            }
            action.submit(AppExecutorUtil.getAppExecutorService());
        }, 1000);
    }

    private Alarm getRefreshPsiFileAlarm() {
        if (refreshPsiFileAlarm == null) {
            synchronized (this) {
                if (refreshPsiFileAlarm == null) {
                    refreshPsiFileAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
                }
            }
        }
        return refreshPsiFileAlarm;
    }

    /**
     * Return the existing LSP file support for the given Psi file, or create a new one if necessary.
     *
     * @param file the Psi file.
     * @return the existing LSP file support for the given Psi file, or create a new one if necessary.
     */
    public static @NotNull LSPFileSupport getSupport(@NotNull PsiFile file) {
        LSPFileSupport support = file.getUserData(LSP_FILE_SUPPORT_KEY);
        if (support == null) {
            // create LSP support by taking care of multiple threads which could call it.
            support = createSupport(file);
        }
        return support;
    }

    private synchronized static @NotNull LSPFileSupport createSupport(@NotNull PsiFile file) {
        LSPFileSupport support = file.getUserData(LSP_FILE_SUPPORT_KEY);
        if (support != null) {
            return support;
        }
        return new LSPFileSupport(file);
    }

    /**
     * Returns true if the given Psi file has LSP support and false otherwise.
     *
     * @param file the Psi file.
     * @return true if the given Psi file has LSP support and false otherwise.
     */
    public static boolean hasSupport(@NotNull PsiFile file) {
        return file.getUserData(LSP_FILE_SUPPORT_KEY) != null;
    }

    public PsiFile getFile() {
        return file;
    }

}

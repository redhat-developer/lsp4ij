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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.codeAction.intention.LSPIntentionCodeActionSupport;
import com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensSupport;
import com.redhat.devtools.lsp4ij.features.color.LSPColorSupport;
import com.redhat.devtools.lsp4ij.features.documentLink.LSPDocumentLinkSupport;
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverSupport;
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeSupport;
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingSupport;
import com.redhat.devtools.lsp4ij.features.highlight.LSPHighlightSupport;
import com.redhat.devtools.lsp4ij.features.inlayhint.LSPInlayHintsSupport;
import com.redhat.devtools.lsp4ij.features.rename.LSPPrepareRenameSupport;
import com.redhat.devtools.lsp4ij.features.rename.LSPRenameSupport;
import com.redhat.devtools.lsp4ij.features.signatureHelp.LSPSignatureHelpSupport;
import org.jetbrains.annotations.NotNull;

/**
 * LSP file support stored in the opened {@link PsiFile} with key "lsp.file.support"
 * which manages and caches LSP codeLens, inlayHint, color futures.
 */
public class LSPFileSupport implements Disposable {

    private static final Key<LSPFileSupport> LSP_FILE_SUPPORT_KEY = Key.create("lsp.file.support");
    private final PsiFile file;

    private final LSPCodeLensSupport codeLensSupport;

    private final LSPInlayHintsSupport inlayHintsSupport;

    private final LSPColorSupport colorSupport;

    private final LSPFoldingRangeSupport foldingRangeSupport;

    private final LSPFormattingSupport formattingSupport;

    private final LSPHighlightSupport highlightSupport;

    private final LSPSignatureHelpSupport signatureHelpSupport;

    private final LSPDocumentLinkSupport documentLinkSupport;

    private final LSPHoverSupport hoverSupport;

    private final LSPIntentionCodeActionSupport intentionCodeActionSupport;

    private final LSPPrepareRenameSupport prepareRenameSupport;

    private final LSPRenameSupport renameSupport;

    private LSPFileSupport(@NotNull PsiFile file) {
        this.file = file;
        this.codeLensSupport = new LSPCodeLensSupport(file);
        this.inlayHintsSupport = new LSPInlayHintsSupport(file);
        this.colorSupport = new LSPColorSupport(file);
        this.foldingRangeSupport = new LSPFoldingRangeSupport(file);
        this.formattingSupport = new LSPFormattingSupport(file);
        this.highlightSupport = new LSPHighlightSupport(file);
        this.signatureHelpSupport = new LSPSignatureHelpSupport(file);
        this.documentLinkSupport = new LSPDocumentLinkSupport(file);
        this.hoverSupport = new LSPHoverSupport(file);
        this.intentionCodeActionSupport = new LSPIntentionCodeActionSupport(file);
        this.prepareRenameSupport = new LSPPrepareRenameSupport(file);
        this.renameSupport = new LSPRenameSupport(file);
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
        getFormattingSupport().cancel();
        getHighlightSupport().cancel();
        getSignatureHelpSupport().cancel();
        getDocumentLinkSupport().cancel();
        getHoverSupport().cancel();
        getIntentionCodeActionSupport().cancel();
        getPrepareRenameSupport().cancel();
        getRenameSupport().cancel();
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
     * Returns the LSP formatting support.
     *
     * @return the LSP formatting support.
     */
    public LSPFormattingSupport getFormattingSupport() {
        return formattingSupport;
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

}

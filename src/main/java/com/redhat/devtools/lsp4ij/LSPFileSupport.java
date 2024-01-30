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
import com.redhat.devtools.lsp4ij.operations.codelens.LSPCodeLensSupport;
import com.redhat.devtools.lsp4ij.operations.color.LSPColorSupport;
import com.redhat.devtools.lsp4ij.operations.inlayhint.LSPInlayHintsSupport;
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

    private LSPFileSupport(@NotNull PsiFile file) {
        this.file = file;
        this.codeLensSupport = new LSPCodeLensSupport(file);
        this.inlayHintsSupport = new LSPInlayHintsSupport(file);
        this.colorSupport = new LSPColorSupport(file);
        file.putUserData(LSP_FILE_SUPPORT_KEY, this);
    }

    @Override
    public void dispose() {
        // cancel all LSP requests
        file.putUserData(LSP_FILE_SUPPORT_KEY, null);
        getCodeLensSupport().cancel();
        getInlayHintsSupport().cancel();
        getColorSupport().cancel();
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

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Client-side editor behavior feature. This does not correspond to an actual LSP feature.
 */
@ApiStatus.Experimental
public class EditorBehaviorFeature {

    private LSPClientFeatures clientFeatures;

    /**
     * Creates the editor behavior feature for the containing client features.
     *
     * @param clientFeatures the client features
     */
    public EditorBehaviorFeature(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Sets the containing client features.
     *
     * @param clientFeatures the client features.
     */
    void setClientFeatures(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Returns the containing client features.
     *
     * @return the client features
     */
    @NotNull
    protected LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }

    /**
     * Whether or not editor improvements for string literals are enabled. Defaults to false.
     *
     * @param file the file
     * @return true if editor improvements for string literals are enabled; otherwise false
     */
    public boolean isEnableStringLiteralImprovements(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * Whether or not editor improvements for statement terminators are enabled. Defaults to false.
     *
     * @param file the file
     * @return true if editor improvements for statement terminators are enabled; otherwise false
     */
    public boolean isEnableStatementTerminatorImprovements(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * Whether or not the fix for <a href="https://youtrack.jetbrains.com/issue/IJPL-159454">IJPL-159454</a> is enabled.
     * Defaults to false.
     *
     * @param file the file
     * @return true if the fix for enter-between-braces behavior is enabled; otherwise false
     */
    public boolean isEnableEnterBetweenBracesFix(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    // Utility methods to check the state of these feature flags easily

    /**
     * Determines whether or not string literal editor improvements are enabled for the specified file.
     *
     * @param file the PSI file
     * @return true if string literal editor improvements are enabled by at least one language server for the file;
     * otherwise false
     */
    public static boolean enableStringLiteralImprovements(@NotNull PsiFile file) {
        return LanguageServiceAccessor.getInstance(file.getProject()).hasAny(
                file.getVirtualFile(),
                ls -> ls.getClientFeatures().getEditorBehaviorFeature().isEnableStringLiteralImprovements(file)
        );
    }

    /**
     * Determines whether or not statement terminator editor improvements are enabled for the specified file.
     *
     * @param file the PSI file
     * @return true if statement terminator editor improvements are enabled by at least one language server for the
     * file; otherwise false
     */
    public static boolean enableStatementTerminatorImprovements(@NotNull PsiFile file) {
        return LanguageServiceAccessor.getInstance(file.getProject()).hasAny(
                file.getVirtualFile(),
                ls -> ls.getClientFeatures().getEditorBehaviorFeature().isEnableStatementTerminatorImprovements(file)
        );
    }

    /**
     * Whether or not the fix for <a href="https://youtrack.jetbrains.com/issue/IJPL-159454">IJPL-159454</a> is enabled
     * for the specified file.
     *
     * @param file the PSI file
     * @return true if the fix is enabled by at least one language server for the file; otherwise false
     */
    public static boolean enableEnterBetweenBracesFix(@NotNull PsiFile file) {
        return LanguageServiceAccessor.getInstance(file.getProject()).hasAny(
                file.getVirtualFile(),
                ls -> ls.getClientFeatures().getEditorBehaviorFeature().isEnableEnterBetweenBracesFix(file)
        );
    }
}

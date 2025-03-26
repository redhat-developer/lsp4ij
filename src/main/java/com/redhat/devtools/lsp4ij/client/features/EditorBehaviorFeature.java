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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side editor behavior feature. This does not correspond to an actual LSP feature.
 */
@ApiStatus.Experimental
public class EditorBehaviorFeature {

    private static final Map<String, Key<CachedValue<Boolean>>> CACHE_KEYS = new ConcurrentHashMap<>();

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

    /**
     * Whether or not editor improvements for nested braces/brackets/parentheses in TextMate files are enabled.
     * Defaults to false.
     *
     * @param file the file
     * @return true if editor improvements for nested braces/brackets/parentheses in TextMate files are enabled;
     * otherwise false
     */
    public boolean isEnableTextMateNestedBracesImprovements(@NotNull PsiFile file) {
        // Default to disabled
        return false;
    }

    /**
     * Whether or not the semantic tokens-based file view provider is enabled.
     *
     * @param file the file
     * @return true if the semantic tokens-based file view provider is enabled; otherwise false
     */
    public boolean isEnableSemanticTokensFileViewProvider(@NotNull PsiFile file) {
        // Default to enabled, but a file view provider must be registered for the provided file to be truly enabled
        return true;
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
        return isEnabled(file, EditorBehaviorFeature::isEnableStringLiteralImprovements);
    }

    /**
     * Determines whether or not statement terminator editor improvements are enabled for the specified file.
     *
     * @param file the PSI file
     * @return true if statement terminator editor improvements are enabled by at least one language server for the
     * file; otherwise false
     */
    public static boolean enableStatementTerminatorImprovements(@NotNull PsiFile file) {
        return isEnabled(file, EditorBehaviorFeature::isEnableStatementTerminatorImprovements);
    }

    /**
     * Whether or not the fix for <a href="https://youtrack.jetbrains.com/issue/IJPL-159454">IJPL-159454</a> is enabled
     * for the specified file.
     *
     * @param file the PSI file
     * @return true if the fix is enabled by at least one language server for the file; otherwise false
     */
    public static boolean enableEnterBetweenBracesFix(@NotNull PsiFile file) {
        return isEnabled(file, EditorBehaviorFeature::isEnableEnterBetweenBracesFix);
    }

    /**
     * Determines whether or not editor improvements for nested braces/brackets/parentheses in TextMate files are
     * enabled for the specified file.
     *
     * @param file the PSI file
     * @return true if editor improvements for nested braces/brackets/parentheses in TextMate files are enabled by at
     * least one language server for the file; otherwise false
     */
    public static boolean enableTextMateNestedBracesImprovements(@NotNull PsiFile file) {
        return isEnabled(file, EditorBehaviorFeature::isEnableTextMateNestedBracesImprovements);
    }

    /**
     * Whether or not the semantic tokens-based file view provider is enabled.
     *
     * @param file the PSI file
     * @return true if the semantic tokens-based file view provider is enabled; otherwise false
     */
    public static boolean enableSemanticTokensFileViewProvider(@NotNull PsiFile file) {
        return isEnabled(file, EditorBehaviorFeature::isEnableSemanticTokensFileViewProvider);
    }

    private interface FeatureFlagChecker {
        boolean isEnabled(@NotNull EditorBehaviorFeature editorBehaviorFeature, @NotNull PsiFile file);
    }

    private static boolean isEnabled(
            @NotNull PsiFile file,
            @NotNull FeatureFlagChecker featureFlagChecker
    ) {
        return isEnabled(file.getProject(), file.getVirtualFile(), featureFlagChecker);
    }

    private static boolean isEnabled(
            @NotNull Project project,
            @Nullable VirtualFile virtualFile,
            @NotNull FeatureFlagChecker featureFlagChecker
    ) {
        if (virtualFile == null) return false;

        Key<CachedValue<Boolean>> cacheKey = getCacheKey(virtualFile, featureFlagChecker);
        return CachedValuesManager.getManager(project).getCachedValue(
                project,
                cacheKey,
                () -> {
                    PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
                    if ((file == null) || !file.isValid()) {
                        // If we couldn't find the PSI file, return false/disabled with no caching
                        return Result.create(false, ModificationTracker.EVER_CHANGED);
                    }

                    return Result.create(
                            LanguageServiceAccessor.getInstance(project).hasAny(
                                    file,
                                    ls -> featureFlagChecker.isEnabled(ls.getClientFeatures().getEditorBehaviorFeature(), file)
                            ),
                            // Evict if any language server definition config that could affect the file changes
                            LanguageServiceAccessor.getInstance(project).getModificationTrackers(file).toArray()
                    );
                },
                false
        );
    }

    @NotNull
    private static Key<CachedValue<Boolean>> getCacheKey(
            @NotNull VirtualFile virtualFile,
            @NotNull FeatureFlagChecker featureFlagChecker
    ) {
        // Create a compact cache key if possible
        String virtualFileId = virtualFile instanceof VirtualFileWithId virtualFileWithId ? String.valueOf(virtualFileWithId.getId()) : virtualFile.getPath();
        String cacheKeyName = featureFlagChecker.getClass().getName() + "::" + virtualFileId;
        return CACHE_KEYS.computeIfAbsent(cacheKeyName, Key::create);
    }
}

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
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Client-side editor behavior feature. This does not correspond to an actual LSP feature.
 */
@ApiStatus.Experimental
public class EditorBehaviorFeature {

    private static final Map<String, Key<FlagValue>> CACHE_KEYS = new ConcurrentHashMap<>();

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

        if (!(virtualFile instanceof VirtualFileWithId)) {
            // A file outside the VFS, such as a document that a language server provides with
            // workspace/textDocumentContent, holds its PSI: its flags are computed each time, so that no cache keeps it
            FlagValue flagValue = compute(project, virtualFile, featureFlagChecker);
            return flagValue != null && flagValue.value();
        }
        Key<FlagValue> cacheKey = getCacheKey(virtualFile, featureFlagChecker);
        FlagValue cached = project.getUserData(cacheKey);
        if (cached != null && cached.isUpToDate()) {
            return cached.value();
        }
        FlagValue computed = compute(project, virtualFile, featureFlagChecker);
        if (computed == null) {
            return false;
        }
        project.putUserData(cacheKey, computed);
        return computed.value();
    }

    /**
     * Computes the given flag for the given file from its language servers.
     *
     * @return the flag, or null if the PSI file can't be found: the flag is then false/disabled, with no caching.
     */
    private static @Nullable FlagValue compute(
            @NotNull Project project,
            @NotNull VirtualFile virtualFile,
            @NotNull FeatureFlagChecker featureFlagChecker
    ) {
        PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
        if ((file == null) || !file.isValid()) {
            return null;
        }

        LanguageServiceAccessor languageServiceAccessor = LanguageServiceAccessor.getInstance(project);
        // Evict if any language server definition config that could affect the file changes. The counts are read
        // before the language servers are, see FlagValue. The count of the project's language servers is read first,
        // before the language servers of the file are looked for
        TrackerCount languageServersCount = TrackerCount.of(languageServiceAccessor::getModificationCount);
        List<TrackerCount> trackerCounts = Stream.concat(Stream.of(languageServersCount),
                        languageServiceAccessor.getModificationTrackers(file).stream().map(TrackerCount::of))
                .toList();
        boolean value = languageServiceAccessor.hasAny(
                file,
                ls -> featureFlagChecker.isEnabled(ls.getClientFeatures().getEditorBehaviorFeature(), file)
        );
        return new FlagValue(value, trackerCounts);
    }

    /**
     * A flag of a file, with the modification counts that the language servers of the file had before it was computed
     * from them. It is up to date while none of the counts has changed.
     * <p>
     * The counts are read before the flag is computed. A language server that starts, or whose configuration changes,
     * while the flag is computed changes a count after it was read, and the flag is computed again the next time.
     * Counts read after the computation, as a {@link CachedValue} reads those of its dependencies, can already include
     * a change that the flag missed, and keep the flag until the next change: a file opened as its language server
     * started then kept the flags of a file that no started language server serves, such as a semantic tokens-based
     * file view provider that stays disabled.
     * <p>
     * The count of the project's language servers, which changes as one starts, is read before the language servers
     * of the file are looked for. A language server created after that lookup isn't among them, so its own count isn't
     * kept, but the flag can still be computed from it once it has started. Read after the lookup, that count could
     * already include the start, and the flag would miss the later changes of that language server.
     *
     * @param value         the flag.
     * @param trackerCounts the modification counts read before the flag was computed.
     */
    private record FlagValue(boolean value, @NotNull List<TrackerCount> trackerCounts) {

        boolean isUpToDate() {
            return trackerCounts.stream().allMatch(TrackerCount::isUnchanged);
        }
    }

    /**
     * A modification tracker, with the modification count that it had when it was read.
     */
    private record TrackerCount(@NotNull ModificationTracker tracker, long count) {

        static @NotNull TrackerCount of(@NotNull ModificationTracker tracker) {
            return new TrackerCount(tracker, tracker.getModificationCount());
        }

        boolean isUnchanged() {
            return tracker.getModificationCount() == count;
        }
    }

    @NotNull
    private static Key<FlagValue> getCacheKey(
            @NotNull VirtualFile virtualFile,
            @NotNull FeatureFlagChecker featureFlagChecker
    ) {
        // Create a compact cache key if possible
        String virtualFileId = virtualFile instanceof VirtualFileWithId virtualFileWithId ? String.valueOf(virtualFileWithId.getId()) : virtualFile.getPath();
        String cacheKeyName = featureFlagChecker.getClass().getName() + "::" + virtualFileId;
        return CACHE_KEYS.computeIfAbsent(cacheKeyName, Key::create);
    }
}

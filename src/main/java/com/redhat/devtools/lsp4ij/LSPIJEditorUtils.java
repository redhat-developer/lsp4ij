/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * Provides utility methods for editor support behaviors.
 */
@ApiStatus.Internal
public final class LSPIJEditorUtils {

    private static final String TEXT_MATE_LANGUAGE_ID = "textmate";

    // TODO: Unfortunately the TextMate interface changed in this commit:
    //  https://github.com/JetBrains/intellij-community/commit/8df3d04be0db4c54732a15250b789aa5d9a6de47#diff-08fc4fd41510ee4662c41d3f2a671ae2f654d1a2f6ff7608765f427c26eaeae7
    //  and would now require reflection to work in 2023.2 and later versions. Specifically it used to be
    //  "bracePair.left/right" which returned "char", but now it's "bracePair.getLeft()/getRight()" which return
    //  "CharSequence". I'm leaving the associated changes in but commented out and returning "null" -- existing usages
    //  will degrade gracefully -- and then when all supported IDE versions have the same interface, this can be
    //  restored. Perhaps this even prompts removal of support for the oldest versions that have this issue?

    private LSPIJEditorUtils() {
        // Pure utility class
    }

    /**
     * Determines whether or not the provided file is an abstract file type file and is supported by a configured
     * language server. The latter is important for many editor support behaviors because you don't want to suspend
     * something like end user typing to wait for a server to start.
     *
     * @param file the file
     * @return true if the file has an abstract file type and is supported by a configured language server;
     * otherwise false
     */
    @ApiStatus.Internal
    public static boolean isSupportedAbstractFileTypeFile(@NotNull PsiFile file) {
        return isAbstractFileTypeFile(file) && LanguageServersRegistry.getInstance().isFileSupported(file);
    }

    /**
     * Determines whether or not the provided file is a TextMate file and is supported by a configured language server.
     * The latter is important for many editor support behaviors because you don't want to suspend something like end
     * user typing to wait for a server to start.
     *
     * @param file the file
     * @return true if the file is TextMate and is supported by a configured language server;
     * otherwise false
     */
    @ApiStatus.Internal
    public static boolean isSupportedTextMateFile(@NotNull PsiFile file) {
        return isTextMateFile(file) && LanguageServersRegistry.getInstance().isFileSupported(file);
    }

    /**
     * Determines whether or not the provided file is an abstract file type or TextMate file and is supported by a
     * configured language server. The latter is important for many editor support behaviors because you don't want to
     * suspend something like end user typing to wait for a server to start.
     *
     * @param file the file
     * @return true if the file is either abstract file type or TextMate and is supported by a configured language
     * server; otherwise false
     */
    @ApiStatus.Internal
    public static boolean isSupportedAbstractFileTypeOrTextMateFile(@NotNull PsiFile file) {
        return isSupportedAbstractFileTypeFile(file) || isSupportedTextMateFile(file);
    }

    private static boolean isAbstractFileTypeFile(@NotNull PsiFile file) {
        return file.getFileType() instanceof AbstractFileType;
    }

    private static boolean isTextMateFile(@NotNull PsiFile file) {
        // Compare language IDs to avoid a static reference to anything in the TextMate Bundles plugin
        return TEXT_MATE_LANGUAGE_ID.equals(file.getLanguage().getID());
    }

    /**
     * Returns the TextMate language and null otherwise.
     *
     * @return the TextMate language and null otherwise.
     */
    @Nullable
    public static Language getTextMateLanguage() {
        return Language.findLanguageByID(TEXT_MATE_LANGUAGE_ID);
    }

    // Quote characters

    private static final Set<Character> DEFAULT_QUOTE_CHARACTERS = Set.of('\'', '"', '`');

    /**
     * Returns the quote characters for the file. If it's a TextMate file or associated with an abstract file type,
     * they're determined using the associated configuration data. If not, the defaults of single-quote, double-quote,
     * and back-tick are returned.
     *
     * @param file the file for which quote characters should be returned
     * @return the file's quote characters
     */
    @NotNull
    @ApiStatus.Internal
    public static Set<Character> getQuoteCharacters(@NotNull PsiFile file) {
        Set<Character> quoteCharacters = isAbstractFileTypeFile(file) ? getAbstractFileTypeQuoteCharacters(file) :
                isTextMateFile(file) ? getTextMateQuoteCharacters(file) :
                        null;
        if (quoteCharacters == null) {
            quoteCharacters = DEFAULT_QUOTE_CHARACTERS;
        }
        return quoteCharacters;
    }

    @Nullable
    private static Set<Character> getAbstractFileTypeQuoteCharacters(@NotNull PsiFile file) {
        return isAbstractFileTypeFile(file) ? DEFAULT_QUOTE_CHARACTERS : null;
    }

    @Nullable
    private static Set<Character> getTextMateQuoteCharacters(@NotNull PsiFile file) {
        /* TODO: See TextMate versioning comment above
        Editor editor = isTextMateFile(file) ? LSPIJUtils.editorForElement(file) : null;
        TextMateScope scope = editor instanceof EditorEx editorEx ? TextMateEditorUtils.getCurrentScopeSelector(editorEx) : null;
        List<Preferences> allPreferences = scope != null ? TextMateService.getInstance().getPreferenceRegistry().getPreferences(scope) : Collections.emptyList();
        Preferences preferences = ContainerUtil.getFirstItem(allPreferences);
        Set<TextMateAutoClosingPair> smartTypingPairs = preferences != null ? preferences.getSmartTypingPairs() : Collections.emptySet();
        if (!ContainerUtil.isEmpty(smartTypingPairs)) {
            Set<Character> quoteCharacters = new LinkedHashSet<>();
            for (TextMateAutoClosingPair smartTypingPair : smartTypingPairs) {
                CharSequence left = smartTypingPair.getLeft();
                CharSequence right = smartTypingPair.getRight();
                if ((left.length() == 1) && left.equals(right)) {
                    char quoteCharacterCandidate = left.charAt(0);
                    if (DEFAULT_QUOTE_CHARACTERS.contains(quoteCharacterCandidate)) {
                        quoteCharacters.add(quoteCharacterCandidate);
                    }
                }
            }
            return quoteCharacters;
        }
        */

        return null;
    }

    @ApiStatus.Internal
    public static boolean isQuoteCharacter(@NotNull PsiFile file, char character) {
        return getQuoteCharacters(file).contains(character);
    }

    // Brace pairs

    // Default brace/bracket/parentheses pairs for when we can't derive them from the file type/language
    private static final Map.Entry<Character, Character> BRACES_ENTRY = Map.entry('{', '}');
    private static final Map.Entry<Character, Character> BRACKETS_ENTRY = Map.entry('[', ']');
    private static final Map.Entry<Character, Character> PARENTHESES_ENTRY = Map.entry('(', ')');
    @ApiStatus.Internal
    public static final Map<Character, Character> DEFAULT_BRACE_PAIRS = Map.ofEntries(
            BRACES_ENTRY,
            BRACKETS_ENTRY,
            PARENTHESES_ENTRY
    );

    /**
     * Returns the brace pairs for the file. If it's a TextMate file or associated with an abstract file type, they're
     * determined using the associated configuration data. If not, the defaults of braces, brackets, and parentheses
     * are returned.
     *
     * @param file the file for which brace pairs should be returned
     * @return the brace pairs for the file
     */
    @NotNull
    @ApiStatus.Internal
    public static Map<Character, Character> getBracePairs(@NotNull PsiFile file) {
        Map<Character, Character> bracePairs = isAbstractFileTypeFile(file) ? getAbstractFileTypeBracePairs(file) :
                isTextMateFile(file) ? getTextMateBracePairs(file) :
                        null;
        if (bracePairs == null) {
            bracePairs = DEFAULT_BRACE_PAIRS;
        }
        return bracePairs;
    }

    @Nullable
    private static Map<Character, Character> getAbstractFileTypeBracePairs(@NotNull PsiFile file) {
        if (!isAbstractFileTypeFile(file)) {
            return null;
        }
        AbstractFileType abstractFileType = (AbstractFileType) file.getFileType();

        Map<Character, Character> bracePairs = new HashMap<>();
        SyntaxTable syntaxTable = abstractFileType.getSyntaxTable();
        if (syntaxTable.isHasBraces()) {
            bracePairs.put(BRACES_ENTRY.getKey(), BRACES_ENTRY.getValue());
        }
        if (syntaxTable.isHasBrackets()) {
            bracePairs.put(BRACKETS_ENTRY.getKey(), BRACKETS_ENTRY.getValue());
        }
        if (syntaxTable.isHasParens()) {
            bracePairs.put(PARENTHESES_ENTRY.getKey(), PARENTHESES_ENTRY.getValue());
        }
        return bracePairs;
    }

    @Nullable
    private static Map<Character, Character> getTextMateBracePairs(@NotNull PsiFile file) {
        return null;

        /* TODO: See TextMate versioning comment above
        if (!isTextMateFile(file)) {
            return null;
        }

        Map<Character, Character> bracePairs = new LinkedHashMap<>();
        Editor editor = LSPIJUtils.editorForElement(file);
        TextMateScope selector = editor instanceof EditorEx editorEx ? TextMateEditorUtils.getCurrentScopeSelector(editorEx) : null;
        if (selector != null) {
            for (TextMateBracePair bracePair : getAllPairsForMatcher(selector)) {
                CharSequence openBrace = bracePair.getLeft();
                CharSequence closeBrace = bracePair.getRight();
                if ((openBrace.length() == 1) && (closeBrace.length() == 1)) {
                    bracePairs.put(openBrace.charAt(0), closeBrace.charAt(0));
                }
            }
        }
        return bracePairs;
        */
    }

    /*
    // NOTE: Cloned from TextMateEditorUtils where this is private
    @NotNull
    private static Set<TextMateBracePair> getAllPairsForMatcher(@Nullable TextMateScope selector) {
        if (selector == null) {
            return Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS;
        }
        Set<TextMateBracePair> result = new HashSet<>();
        List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferenceRegistry().getPreferences(selector);
        for (Preferences preferences : preferencesForSelector) {
            final Set<TextMateBracePair> highlightingPairs = preferences.getHighlightingPairs();
            if (highlightingPairs != null) {
                if (highlightingPairs.isEmpty()) {
                    // smart typing pairs can be defined in preferences but can be empty (in order to disable smart typing completely)
                    return Collections.emptySet();
                }
                result.addAll(highlightingPairs);
            }
        }
        return result;
    }
    */

    /**
     * Determines whether or not the specified character can open a brace pair in the provided file.
     *
     * @param file      the PSI file
     * @param character the optional character
     * @return true if the character can open a brace pair in the file; otherwise false
     */
    @ApiStatus.Internal
    public static boolean isOpenBraceCharacter(@NotNull PsiFile file, @Nullable Character character) {
        return (character != null) && getBracePairsFwd(file).containsKey(character);
    }

    /**
     * Determines whether or not the specified character can close a brace pair in the provided file.
     *
     * @param file      the PSI file
     * @param character the optional character
     * @return true if the character can close a brace pair in the file; otherwise false
     */
    @ApiStatus.Internal
    public static boolean isCloseBraceCharacter(@NotNull PsiFile file, @Nullable Character character) {
        return (character != null) && getBracePairsBwd(file).containsKey(character);
    }

    /**
     * Returns the brace pair open character for the specified brace pair close character in the provided file.
     *
     * @param file                the PSI file
     * @param closeBraceCharacter the optional brace pair close character
     * @return the corresponding brace pair open character if found; otherwise null
     */
    @Nullable
    @ApiStatus.Internal
    public static Character getOpenBraceCharacter(@NotNull PsiFile file, @Nullable Character closeBraceCharacter) {
        return closeBraceCharacter != null ? getBracePairsBwd(file).get(closeBraceCharacter) : null;
    }

    /**
     * Returns the brace pair close character for the specified brace pair open character in the provided file.
     *
     * @param file               the PSI file
     * @param openBraceCharacter the optional brace pair open character
     * @return the corresponding brace pair close character if found; otherwise null
     */
    @Nullable
    @ApiStatus.Internal
    public static Character getCloseBraceCharacter(@NotNull PsiFile file, @Nullable Character openBraceCharacter) {
        return openBraceCharacter != null ? getBracePairsFwd(file).get(openBraceCharacter) : null;
    }

    @NotNull
    private static Map<Character, Character> getBracePairsFwd(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(getBracePairs(file), file));
    }

    @NotNull
    private static Map<Character, Character> getBracePairsBwd(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(ContainerUtil.reverseMap(getBracePairsFwd(file)), file));
    }

    // Comments

    @NotNull
    public static Commenter getCommenter(@NotNull PsiFile file) {
        Commenter commenter = null;

        // If it has an abstract file type, we can get comment information from it
        if (isAbstractFileTypeFile(file)) {
            AbstractFileType abstractFileType = (AbstractFileType) file.getFileType();
            commenter = abstractFileType.getCommenter();
        }

        // NOTE: Unfortunately the comment information is not available from TextMate bundles

        // Otherwise try to get it from the client features
        if (commenter == null) {
            commenter = new ClientFeaturesBasedCommenter(file);
        }

        return commenter;
    }

    private static class ClientFeaturesBasedCommenter implements Commenter {
        private final String lineCommentPrefix;
        private final String blockCommentPrefix;
        private final String blockCommentSuffix;

        private ClientFeaturesBasedCommenter(@NotNull PsiFile file) {
            LSPClientFeatures clientFeatures = getClientFeatures(file);
            this.lineCommentPrefix = clientFeatures != null ? clientFeatures.getLineCommentPrefix(file) : null;
            this.blockCommentPrefix = clientFeatures != null ? clientFeatures.getBlockCommentPrefix(file) : null;
            this.blockCommentSuffix = clientFeatures != null ? clientFeatures.getBlockCommentSuffix(file) : null;
        }

        @Override
        public @Nullable String getLineCommentPrefix() {
            return lineCommentPrefix;
        }

        @Override
        public @Nullable String getBlockCommentPrefix() {
            return blockCommentPrefix;
        }

        @Override
        public @Nullable String getBlockCommentSuffix() {
            return blockCommentSuffix;
        }

        @Override
        public @Nullable String getCommentedBlockCommentPrefix() {
            return null;
        }

        @Override
        public @Nullable String getCommentedBlockCommentSuffix() {
            return null;
        }

        @Nullable
        private static LSPClientFeatures getClientFeatures(@NotNull PsiFile file) {
            CompletableFuture<List<LanguageServerItem>> languageServersFuture = LanguageServiceAccessor.getInstance(file.getProject()).getLanguageServers(
                    file.getVirtualFile(),
                    clientFeatures -> StringUtil.isNotEmpty(clientFeatures.getLineCommentPrefix(file)) ||
                                      StringUtil.isNotEmpty(clientFeatures.getBlockCommentPrefix(file)) ||
                                      StringUtil.isNotEmpty(clientFeatures.getBlockCommentSuffix(file)),
                    null
            );
            //noinspection TryWithIdenticalCatches
            try {
                waitUntilDone(languageServersFuture, file);
            } catch (ProcessCanceledException e) {
                return null;
            } catch (CancellationException e) {
                return null;
            } catch (ExecutionException e) {
                return null;
            }
            if (!isDoneNormally(languageServersFuture)) {
                return null;
            }

            List<LanguageServerItem> languageServers = languageServersFuture.getNow(Collections.emptyList());
            Set<LSPClientFeatures> matchingClientFeatures = languageServers
                    .stream()
                    .filter(Objects::nonNull)
                    .map(LanguageServerItem::getClientFeatures)
                    .collect(Collectors.toSet());
            // TODO: If there are multiple, can/should be merge them?
            return matchingClientFeatures.size() == 1 ? ContainerUtil.getFirstItem(matchingClientFeatures) : null;
        }
    }
}

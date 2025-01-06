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

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Utilities for working with TextMate files in LSP4IJ.
 */
@ApiStatus.Internal
public final class LSPIJTextMateUtils {

    private LSPIJTextMateUtils() {
        // Pure utility class
    }

    /**
     * Returns the simple/single-character brace pairs for the file if it's a TextMate file.
     *
     * @param file the PSI file
     * @return the simple brace pairs for the file if it's a TextMate file; otherwise null
     */
    @Nullable
    @ApiStatus.Internal
    public static Map<Character, Character> getBracePairs(@NotNull PsiFile file) {
        // TODO: Unfortunately the interface changed in this commit:
        //  https://github.com/JetBrains/intellij-community/commit/8df3d04be0db4c54732a15250b789aa5d9a6de47#diff-08fc4fd41510ee4662c41d3f2a671ae2f654d1a2f6ff7608765f427c26eaeae7
        //  and would now require reflection to work in 2023.2 and later versions. Specifically it used to be
        //  "bracePair.left/right" which returned "char", but now it's "bracePair.getLeft()/getRight()" which return
        //  "CharSequence". I think it's worth leaving this in but commented out and returning "null" -- existing usages
        //  will degrade gracefully -- and then when all supported IDE versions have the same interface, this can be
        //  restored. Perhaps this even prompts removal of support for the oldest versions that have this issue?
        return null;

/*
        if (!(file instanceof TextMateFile)) {
            return null;
        }

        Map<Character, Character> bracePairs = new LinkedHashMap<>();
        Editor editor = LSPIJUtils.editorForElement(file);
        TextMateScope selector = editor instanceof EditorEx ? TextMateEditorUtils.getCurrentScopeSelector((EditorEx) editor) : null;
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
}

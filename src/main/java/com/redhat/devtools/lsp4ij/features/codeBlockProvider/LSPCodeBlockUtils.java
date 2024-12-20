/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.codeBlockProvider;

import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJTextMateUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;

import java.util.Map;
import java.util.Set;

/**
 * Utilities for deriving information about code blocks.
 */
@ApiStatus.Internal
public final class LSPCodeBlockUtils {

    // NOTE: JetBrains has maintained a long assumption that these are the primary structural block delimiters via
    // AbstractFileType's explicit support for them. If/when other structural block delimiters are discovered for
    // languages supported by LSP, we can revisit this hard-coded assumption.
    private static final Map<Character, Character> BRACE_PAIR_CHARS_FWD = Map.of(
            '{', '}',
            '[', ']',
            '(', ')'
    );
    private static final Map<Character, Character> BRACE_PAIR_CHARS_BWD = ContainerUtil.reverseMap(BRACE_PAIR_CHARS_FWD);

    private LSPCodeBlockUtils() {
        // Pure utility class
    }

    /**
     * Determines whether or not the specified character can start a code block in the provided file.
     *
     * @param file      the PSI file
     * @param character the optional character
     * @return true if the character can start a code block in the file; otherwise false
     */
    @ApiStatus.Internal
    public static boolean isCodeBlockStartChar(@NotNull PsiFile file, @Nullable Character character) {
        if (character == null) {
            return false;
        }

        Set<TextMateBracePair> bracePairs = LSPIJTextMateUtils.getSimpleTextMateBracePairs(file);
        if (!bracePairs.isEmpty()) {
            String characterString = String.valueOf(character);
            return ContainerUtil.exists(
                    bracePairs,
                    bracePair -> characterString.equals(String.valueOf(bracePair.getLeft()))
            );
        }

        return BRACE_PAIR_CHARS_FWD.containsKey(character);
    }

    /**
     * Determines whether or not the specified character can end a code block in the provided file.
     *
     * @param file      the PSI file
     * @param character the optional character
     * @return true if the character can end a code block in the file; otherwise false
     */
    @ApiStatus.Internal
    public static boolean isCodeBlockEndChar(@NotNull PsiFile file, @Nullable Character character) {
        if (character == null) {
            return false;
        }

        Set<TextMateBracePair> bracePairs = LSPIJTextMateUtils.getSimpleTextMateBracePairs(file);
        if (!bracePairs.isEmpty()) {
            String characterString = String.valueOf(character);
            return ContainerUtil.exists(
                    bracePairs,
                    bracePair -> characterString.equals(String.valueOf(bracePair.getRight()))
            );
        }

        return BRACE_PAIR_CHARS_BWD.containsKey(character);
    }

    /**
     * Returns the code block start character for the specified code block end character in the provided file.
     *
     * @param file             the PSI file
     * @param codeBlockEndChar the optional code block end character
     * @return the corresponding code block start character if found; otherwise null
     */
    @Nullable
    @ApiStatus.Internal
    public static Character getCodeBlockStartChar(@NotNull PsiFile file, @Nullable Character codeBlockEndChar) {
        if (codeBlockEndChar == null) {
            return null;
        }

        Set<TextMateBracePair> bracePairs = LSPIJTextMateUtils.getSimpleTextMateBracePairs(file);
        if (!bracePairs.isEmpty()) {
            String closeBraceCharString = String.valueOf(codeBlockEndChar);
            TextMateBracePair bracePair = ContainerUtil.find(
                    bracePairs,
                    bracePairCandidate -> closeBraceCharString.equals(String.valueOf(bracePairCandidate.getRight()))
            );
            return bracePair != null ? bracePair.getLeft().charAt(0) : null;
        }

        return BRACE_PAIR_CHARS_BWD.get(codeBlockEndChar);
    }

    /**
     * Returns the code block end character for the specified code block start character in the provided file.
     *
     * @param file               the PSI file
     * @param codeBlockStartChar the optional code block start character
     * @return the corresponding code block end character if found; otherwise null
     */
    @Nullable
    @ApiStatus.Internal
    public static Character getCodeBlockEndChar(@NotNull PsiFile file, @Nullable Character codeBlockStartChar) {
        if (codeBlockStartChar == null) {
            return null;
        }

        Set<TextMateBracePair> bracePairs = LSPIJTextMateUtils.getSimpleTextMateBracePairs(file);
        if (!bracePairs.isEmpty()) {
            String openBraceCharString = String.valueOf(codeBlockStartChar);
            TextMateBracePair bracePair = ContainerUtil.find(
                    bracePairs,
                    bracePairCandidate -> openBraceCharString.equals(String.valueOf(bracePairCandidate.getLeft()))
            );
            return bracePair != null ? bracePair.getRight().charAt(0) : null;
        }

        return BRACE_PAIR_CHARS_FWD.get(codeBlockStartChar);
    }
}

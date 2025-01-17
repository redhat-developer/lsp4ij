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

package com.redhat.devtools.lsp4ij.features.codeBlockProvider;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJTextMateUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.psi.TextMateFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for deriving information about code blocks.
 */
@ApiStatus.Internal
public final class LSPCodeBlockUtils {

    private static final Map.Entry<Character, Character> BRACES_ENTRY = Map.entry('{', '}');
    private static final Map.Entry<Character, Character> BRACKETS_ENTRY = Map.entry('[', ']');
    private static final Map.Entry<Character, Character> PARENTHESES_ENTRY = Map.entry('(', ')');

    // NOTE: JetBrains has maintained a long assumption that these are the primary structural block delimiters via
    // AbstractFileType's explicit support for them. If/when other structural block delimiters are discovered for
    // languages supported by LSP, we can revisit this hard-coded assumption.
    private static final Map<Character, Character> DEFAULT_BRACE_PAIRS = Map.ofEntries(
            BRACES_ENTRY,
            BRACKETS_ENTRY,
            PARENTHESES_ENTRY
    );

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
        return (character != null) && getBracePairsFwd(file).containsKey(character);
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
        return (character != null) && getBracePairsBwd(file).containsKey(character);
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
        return codeBlockEndChar != null ? getBracePairsBwd(file).get(codeBlockEndChar) : null;
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
        return codeBlockStartChar != null ? getBracePairsFwd(file).get(codeBlockStartChar) : null;
    }

    @NotNull
    @ApiStatus.Internal
    public static Map<Character, Character> getBracePairs(@NotNull PsiFile file) {
        return getBracePairsFwd(file);
    }

    @NotNull
    private static Map<Character, Character> getBracePairsFwd(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Map<Character, Character> bracePairs = file instanceof TextMateFile ? LSPIJTextMateUtils.getBracePairs(file) :
                    file.getFileType() instanceof AbstractFileType ? getAbstractFileTypeBracePairs(file) :
                            null;
            if (bracePairs == null) {
                bracePairs = DEFAULT_BRACE_PAIRS;
            }
            return Result.create(bracePairs, file);
        });
    }

    @NotNull
    private static Map<Character, Character> getBracePairsBwd(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> Result.create(ContainerUtil.reverseMap(getBracePairsFwd(file)), file));
    }

    @Nullable
    private static Map<Character, Character> getAbstractFileTypeBracePairs(@NotNull PsiFile file) {
        FileType fileType = file.getFileType();
        if (!(fileType instanceof AbstractFileType abstractFileType)) {
            return null;
        }

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
}

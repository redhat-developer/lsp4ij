/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetIndentOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for LSP completion.
 */
public final class CompletionProposalTools {

    private CompletionProposalTools() {
        // to avoid instances, requested by sonar
    }

    // ----------------- Prefix utilities

    public static @Nullable String getCompletionPrefix(@NotNull Position completionPos, @NotNull Range textEditRange, @NotNull Document document) {
        if (Ranges.containsPosition(textEditRange, completionPos)) {
            // ex : {#ea|ch
            // here the prefix to return should be {#ea
            int lineStartOffset = document.getLineStartOffset(completionPos.getLine());
            int startOffset = lineStartOffset + textEditRange.getStart().getCharacter();
            int endOffset = lineStartOffset + completionPos.getCharacter();
            return document.getCharsSequence().subSequence(startOffset, endOffset).toString();
        } else {
            return null;
        }
    }

    // ----------------- Snippet utilities

    /**
     * Returns the indent options to use to format the given snippet text block and null otherwise.
     *
     * @param snippetTextBlock the snippet text block (ex : foo\t\nbar).
     * @param file             the file where thesnippet must be inserted.
     * @return the indent options to use to format the given snippet text block and null otherwise.
     */
    public static @Nullable LspSnippetIndentOptions createLspIndentOptions(String snippetTextBlock, PsiFile file) {
        if (LspSnippetIndentOptions.shouldBeFormatted(snippetTextBlock)) {
            // Get global line separator settings
            CodeStyleSettings settings = CodeStyleSettings.getDefaults();
            String lineSeparator = settings.getLineSeparator();
            CommonCodeStyleSettings.@NotNull IndentOptions indentOptions = getIndentOptions(file, settings);
            boolean insertSpaces = !indentOptions.USE_TAB_CHARACTER;
            int tabSize = indentOptions.TAB_SIZE;
            return new LspSnippetIndentOptions(tabSize, insertSpaces, lineSeparator);
        }
        return null;
    }

    /**
     * Returns the Intellij indent options for the given file and the global indent options otherwise.
     *
     * @param file     the file.
     * @param settings the global code style settings.
     * @return indent options for the given file and the global indent options otherwise.
     */
    private @NotNull
    static CommonCodeStyleSettings.@NotNull IndentOptions getIndentOptions(PsiFile file, CodeStyleSettings settings) {
        Project project = file.getProject();
        FileViewProvider provider = PsiManagerEx.getInstanceEx(project).findViewProvider(file.getVirtualFile());
        if (provider instanceof TemplateLanguageFileViewProvider) {
            // get indent options of the language
            Language language = ((TemplateLanguageFileViewProvider) provider).getTemplateDataLanguage();
            CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(language);
            if (indentOptions != null) {
                return indentOptions;
            }
        }
        Language language = provider.getBaseLanguage();
        CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(language);
        if (indentOptions != null) {
            return indentOptions;
        }
        return settings.getIndentOptions();
    }

}

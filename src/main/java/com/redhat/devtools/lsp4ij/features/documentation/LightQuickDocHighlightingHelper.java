// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.documentation.DocumentationSettings;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.xml.util.XmlStringUtil;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is a copy/paste from JetBrains code
 * <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/lang-impl/src/com/intellij/lang/documentation/QuickDocHighlightingHelper.kt">QuickDocHighlightingHelper.kt</a>
 * <p>
 * translated into Java, only containing methods used to highlight code.
 * <p>
 * When LSP4IJ will have minimal version with 2024, this class can be removed.
 */
public class LightQuickDocHighlightingHelper {

    @RequiresReadLock
    public static @NlsSafe String getStyledSignatureFragment(Project project, Language language, @NlsSafe String code) {
        StringBuilder builder = new StringBuilder();
        appendStyledSignatureFragment(builder, project, language, code);
        return builder.toString();
    }

    @RequiresReadLock
    public static void appendStyledSignatureFragment(StringBuilder builder, Project project, Language language, @NlsSafe String code) {
        appendHighlightedCode(builder, project, language, DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), code, false, false);
    }

    public static Language guessLanguage(String language) {
        if (language == null) {
            return null;
        }
        Language lang = Language.findInstancesByMimeType(language).stream().findFirst().orElse(null);
        if (lang != null) {
            return lang;
        }
        lang = Language.findInstancesByMimeType("text/" + language).stream().findFirst().orElse(null);
        if (lang != null) {
            return lang;
        }
        return Language.getRegisteredLanguages().stream().filter(l -> languageIdOrNameMatches(language, l)).findFirst().orElseGet(() -> findLanguageByFileExtension(language));
    }

    private static void appendHighlightedCode(StringBuilder builder, Project project, Language language, boolean doHighlighting, CharSequence code, boolean isForRenderedDoc, boolean trim) {
        String processedCode = code.toString().trim().replace('\u00A0', ' ');
        if (trim) {
            processedCode = processedCode.trim();
        }
        if (language != null && doHighlighting && LanguageParserDefinitions.INSTANCE.forLanguage(language) != null) {
            try {
                HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                        builder, project, language, processedCode, trim, DocumentationSettings.getHighlightingSaturation(isForRenderedDoc));
            } catch (Exception e) {
                if (e instanceof ControlFlowException) throw e;
                Logger.getInstance(LightQuickDocHighlightingHelper.class).error("Failed to highlight code fragment with language " + language, e);
                builder.append(XmlStringUtil.escapeString(processedCode));
            }
        } else {
            builder.append(XmlStringUtil.escapeString(processedCode));
        }
    }

    private static boolean languageIdOrNameMatches(String langType, Language language) {
        return langType.equalsIgnoreCase(language.getID()) || langType.equalsIgnoreCase(language.getDisplayName());
    }

    private static Language findLanguageByFileExtension(String language) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(language);
        Set<Language> candidates = Language.getRegisteredLanguages().stream()
                .filter(lang -> lang.getAssociatedFileType() == fileType)
                .collect(Collectors.toSet());

        // Let's choose the most basic version of language supporting the particular file extension
        return candidates.stream().filter(candidate -> {
            Language baseLang = candidate.getBaseLanguage();
            while (baseLang != null) {
                if (candidates.contains(baseLang)) {
                    return false;
                }
                baseLang = baseLang.getBaseLanguage();
            }
            return true;
        }).findFirst().orElse(null);
    }

}
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
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.redhat.devtools.lsp4ij.LSPIJTextMateUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple supported languages by LSP ('TEXT' and 'textmate').
 */
public class SimpleLanguageUtils {

    private static final Set<Language> supportedSimpleLanguages;

    static {
        Set<Language> languages = new HashSet<>();
        // plain/text
        languages.add(PlainTextLanguage.INSTANCE);
        // textmate
        Language textMateLanguage = LSPIJTextMateUtils.getTextMateLanguage();
        if (textMateLanguage != null) {
            languages.add(textMateLanguage);
        }
        supportedSimpleLanguages = Collections.unmodifiableSet(languages);
    }

    private SimpleLanguageUtils() {

    }

    /**
     * Returns the supported simple languages (TEXT, textmate) supported by default by LSP4IJ.
     *
     * @return the supported simple languages (TEXT, textmate) supported by default by LSP4IJ.
     */
    public static Set<Language> getSupportedSimpleLanguages() {
        return supportedSimpleLanguages;
    }

    /**
     * Returns true if the given <code>language</code> is a supported language (TEXT, textmate) and false otherwise.
     *
     * @param language the language.
     * @return true if the given <code>language</code> is a supported language (TEXT, textmate) and false otherwise.
     */
    public static boolean isSupported(Language language) {
        return supportedSimpleLanguages.contains(language);
    }
}

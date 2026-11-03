/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests that {@link LSPUsageElementDescriptionProvider#splitIntoWords(String)} splits semantic token types as
 * {@code NameUtil.splitNameIntoWords} did for ASCII names.
 */
public class LSPUsageElementDescriptionProviderTest {

    @Test
    public void theStandardTokenTypesAreSplitAtCapitals() {
        for (String tokenType : List.of("namespace", "type", "class", "enum", "interface", "struct", "parameter",
                "variable", "property", "event", "function", "method", "macro", "keyword", "modifier", "comment",
                "string", "number", "regexp", "operator", "decorator", "label")) {
            assertEquals(List.of(tokenType), LSPUsageElementDescriptionProvider.splitIntoWords(tokenType));
        }
        assertEquals(List.of("type", "Parameter"), LSPUsageElementDescriptionProvider.splitIntoWords("typeParameter"));
        assertEquals(List.of("enum", "Member"), LSPUsageElementDescriptionProvider.splitIntoWords("enumMember"));
    }

    @Test
    public void capitalsDigitsUnderscoresAndOtherCharactersSeparateWords() {
        assertEquals(List.of("HTML", "Element", "2"), LSPUsageElementDescriptionProvider.splitIntoWords("HTMLElement2"));
        assertEquals(List.of("vector", "2", "D"), LSPUsageElementDescriptionProvider.splitIntoWords("vector2D"));
        assertEquals(List.of("type", "name"), LSPUsageElementDescriptionProvider.splitIntoWords("type_name"));
        assertEquals(List.of("builtin", "-", "type"), LSPUsageElementDescriptionProvider.splitIntoWords("builtin-type"));
        assertEquals(List.of("ID"), LSPUsageElementDescriptionProvider.splitIntoWords("ID"));
    }

    @Test
    public void aLetterOutsideTheBasicMultilingualPlaneIsOneLetter() {
        // U+20BB7 is an ideograph, U+10400 and U+10401 are Deseret capital letters, and U+10429 is a Deseret small letter
        String ideograph = Character.toString(0x20BB7);
        String capitalI = Character.toString(0x10400);
        String capitalE = Character.toString(0x10401);
        String smallE = Character.toString(0x10429);
        assertEquals(List.of(ideograph, "Name"), LSPUsageElementDescriptionProvider.splitIntoWords(ideograph + "Name"));
        assertEquals(List.of(capitalI, capitalE + smallE),
                LSPUsageElementDescriptionProvider.splitIntoWords(capitalI + capitalE + smallE));
    }

    @Test
    public void onlyALowerCaseLetterTakesTheLastCapitalOfARun() {
        assertEquals(List.of("HTML", "中文"), LSPUsageElementDescriptionProvider.splitIntoWords("HTML中文"));
    }
}

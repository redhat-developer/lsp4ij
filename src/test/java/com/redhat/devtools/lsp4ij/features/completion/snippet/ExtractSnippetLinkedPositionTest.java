/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion.snippet;

import com.redhat.devtools.lsp4ij.features.completion.snippet.handler.LinkedPositionResult;
import org.junit.Test;

public class ExtractSnippetLinkedPositionTest {

    @Test
    public void linkedPositions() {
        LinkedPositionResult actual = LspSnippetAssert.parseLinkedPosition(
                "{#for ${1:item} in ${2:items}}\n\t{${1:item}.${3:name}}$0\n{/for}", null);
        LspSnippetAssert.assertEquals(actual, "{#for item in items}\n\t{item.name}\n{/for}", //
                LspSnippetAssert.position("item", 6, 4), //
                LspSnippetAssert.position("items", 14, 5), //
                LspSnippetAssert.position("item", 23, 4), //
                LspSnippetAssert.position("name", 28, 4));
    }

    @Test
    public void linkedPositionsWithIndentOptions() {
        LspSnippetIndentOptions indentOptions = new LspSnippetIndentOptions(4, true, "\r\n");
        LinkedPositionResult actual = LspSnippetAssert.parseLinkedPosition(
                "{#for ${1:item} in ${2:items}}\n\t{${1:item}.${3:name}}$0\n{/for}", indentOptions);
        LspSnippetAssert.assertEquals(actual, "{#for item in items}\r\n    {item.name}\r\n{/for}", //
                LspSnippetAssert.position("item", 6, 4), //
                LspSnippetAssert.position("items", 14, 5), //
                LspSnippetAssert.position("item", 27, 4), //
                LspSnippetAssert.position("name", 32, 4));
    }
    
    @Test
    public void linkedPositionsWithIndentOptionsAndCLRF() {
        LspSnippetIndentOptions indentOptions = new LspSnippetIndentOptions(4, true, "\r\n");
        LinkedPositionResult actual = LspSnippetAssert.parseLinkedPosition(
                "{#for ${1:item} in ${2:items}}\r\n\t{${1:item}.${3:name}}$0\r\n{/for}", indentOptions);
        LspSnippetAssert.assertEquals(actual, "{#for item in items}\r\n    {item.name}\r\n{/for}", //
                LspSnippetAssert.position("item", 6, 4), //
                LspSnippetAssert.position("items", 14, 5), //
                LspSnippetAssert.position("item", 27, 4), //
                LspSnippetAssert.position("name", 32, 4));
    }
}

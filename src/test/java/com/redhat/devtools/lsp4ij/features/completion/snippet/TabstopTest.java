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

import com.redhat.devtools.lsp4ij.features.completion.snippet.handler.LspSnippetNode;
import org.junit.Test;

public class TabstopTest {

    @Test
    public void onlyTabstop() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("$123");
        LspSnippetAssert.assertEquals(actual, LspSnippetAssert.tabstop(123));
    }

    @Test
    public void tabstopWithText() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("abcd $123 efgh");
        LspSnippetAssert.assertEquals(actual,
                LspSnippetAssert.text("abcd "), //
                LspSnippetAssert.tabstop(123), //
                LspSnippetAssert.text(" efgh"));
    }

    @Test
    public void tabstopInBracket() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${123}");
        LspSnippetAssert.assertEquals(actual, LspSnippetAssert.tabstop(123));
    }

    @Test
    public void tabstopInBracketWithText() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("abcd ${123} efgh");
        LspSnippetAssert.assertEquals(actual, LspSnippetAssert.text("abcd "), //
                LspSnippetAssert.tabstop(123), //
                LspSnippetAssert.text(" efgh"));
    }

}

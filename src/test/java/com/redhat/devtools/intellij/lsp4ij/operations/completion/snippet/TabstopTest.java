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
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet;

import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.handler.LspSnippetNode;
import org.junit.Test;

import static com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetAssert.*;

public class TabstopTest {

    @Test
    public void onlyTabstop() {
        LspSnippetNode[] actual = parse("$123");
        assertEquals(actual, tabstop(123));
    }

    @Test
    public void tabstopWithText() {
        LspSnippetNode[] actual = parse("abcd $123 efgh");
        assertEquals(actual,
                text("abcd "), //
                tabstop(123), //
                text(" efgh"));
    }

    @Test
    public void tabstopInBracket() {
        LspSnippetNode[] actual = parse("${123}");
        assertEquals(actual, tabstop(123));
    }

    @Test
    public void tabstopInBracketWithText() {
        LspSnippetNode[] actual = parse("abcd ${123} efgh");
        assertEquals(actual, text("abcd "), //
                tabstop(123), //
                text(" efgh"));
    }

}

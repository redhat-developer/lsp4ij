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

import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetAssert.*;

public class PlaceholderTest {

    @Test
    public void placeholder() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:name}");
        assertEquals(actual, LspSnippetAssert.placeholder(1, "name", 1));
    }

    @Test
    public void placeholderWithoutName() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("${1:}");
        assertEquals(actual, LspSnippetAssert.tabstop(1));
    }

    @Test
    public void placeholderWithEscapeDollar() {
        LspSnippetNode[] actual = LspSnippetAssert.parse("\\$${1:var}");
        assertEquals(actual, LspSnippetAssert.text("$"),
                LspSnippetAssert.placeholder(1, "var", 1));
    }
}

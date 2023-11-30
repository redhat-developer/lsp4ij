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

import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.handler.*;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;

public class LspSnippetAssert {
    private LspSnippetAssert() {

    }

    public static LspSnippetNode[] parse(String snippet) {
        LspSnippetHandlerImpl handler = new LspSnippetHandlerImpl();
        LspSnippetParser parser = new LspSnippetParser(handler);
        parser.parse(snippet);
        return handler.getNodes();
    }

    public static LinkedPositionResult parseLinkedPosition(String snippet, LspSnippetIndentOptions indentOptions) {
        ExtractSnippetLinkedPositionHandler handler = new ExtractSnippetLinkedPositionHandler(indentOptions);
        LspSnippetParser parser = new LspSnippetParser(handler);
        parser.parse(snippet);
        return handler.getResult();
    }

    public static void assertEquals(LspSnippetNode[] actual, LspSnippetNode... expected) {
        assertArrayEquals(expected, actual);
    }

    public static TabstopNode tabstop(int index) {
        return new TabstopNode(index);
    }

    public static VariableNode variable(String name) {
        return new VariableNode(name);
    }

    public static TextNode text(String text) {
        return new TextNode(text);
    }

    public static PlaceholderNode placeholder(int index, String name, int level) {
        return new PlaceholderNode(index, name, level);
    }

    public static ChoiceNode choice(int index, String... choices) {
        return choice(index, null, choices);
    }

    public static ChoiceNode choice(String name, String... choices) {
        return choice(null, name, choices);
    }

    private static ChoiceNode choice(Integer index, String name, String... choices) {
        List<String> list = Arrays.stream(choices).collect(Collectors.toList());
        return new ChoiceNode(index, name, list);
    }

    public static void assertEquals(LinkedPositionResult actual, String templateContent, LinkedPosition... positions) {
        Assert.assertEquals(templateContent, actual.getTemplateContent());
        assertArrayEquals(positions, actual.getLinkedPositions());
    }

    public static LinkedPosition position(String name, int offset, int length) {
        return new LinkedPosition(name, offset, length);
    }
}

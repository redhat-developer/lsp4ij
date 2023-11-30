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
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.handler;

import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetHandler;

import java.util.ArrayList;
import java.util.List;

public class LspSnippetHandlerImpl implements LspSnippetHandler {

    private final List<LspSnippetNode> nodes;

    private final List<PlaceholderNode> placeholderStack;

    public LspSnippetHandlerImpl() {
        this.nodes = new ArrayList<>();
        this.placeholderStack = new ArrayList<>();
    }

    @Override
    public void startSnippet() {

    }

    @Override
    public void endSnippet() {

    }

    @Override
    public void text(String text) {
        nodes.add(new TextNode(text));
    }

    @Override
    public void tabstop(int index) {
        nodes.add(new TabstopNode(index));
    }

    @Override
    public void variable(String name) {
        nodes.add(new VariableNode(name));
    }

    @Override
    public void startPlaceholder(int index, String name, int level) {
        PlaceholderNode placeholder = new PlaceholderNode(index, name, level);
        placeholderStack.add(placeholder);
        nodes.add(placeholder);
    }

    @Override
    public void endPlaceholder(int level) {
        placeholderStack.remove(level - 1);
    }

    @Override
    public void choice(int index, List<String> choices) {
        nodes.add(new ChoiceNode(index, null, choices));
    }

    @Override
    public void choice(String name, List<String> choices) {
        nodes.add(new ChoiceNode(null, name, choices));
    }

    public LspSnippetNode[] getNodes() {
        return nodes.toArray(new LspSnippetNode[nodes.size()]);
    }

}

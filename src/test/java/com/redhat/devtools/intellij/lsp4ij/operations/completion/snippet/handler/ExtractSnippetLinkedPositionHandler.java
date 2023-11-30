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

import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.DefaultLspSnippetHandler;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetIndentOptions;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetVariableConstants;

import java.util.ArrayList;
import java.util.List;

public class ExtractSnippetLinkedPositionHandler extends DefaultLspSnippetHandler {

    private final List<LinkedPosition> linkedPositions;

    public ExtractSnippetLinkedPositionHandler(LspSnippetIndentOptions indentOptions) {
        super(name -> {
            switch (name) {
                case LspSnippetVariableConstants.TM_FILENAME:
                    return "foo.txt";
            }
            return name;
        }, indentOptions);
        this.linkedPositions = new ArrayList<>();
    }

    @Override
    public void startPlaceholder(int index, String name, int level) {
        // ex : ${1:name}
        addLinkedPosition(name);
        super.startPlaceholder(index, name, level);
    }

    @Override
    public void variable(String name) {
        String resolved = super.resolveVariable(name);
        if (resolved == null) {
            addLinkedPosition(name);
        }
        super.variable(resolved != null ? resolved : name);
    }

    @Override
    public void choice(int index, List<String> choices) {
        String value = choices.isEmpty() ? "" : choices.get(0);
        addLinkedPosition(value);
        super.choice(index, choices);
    }

    @Override
    public void choice(String name, List<String> choices) {
        addLinkedPosition(name);
        super.choice(name, choices);
    }

    private void addLinkedPosition(String name) {
        int offset = getCurrentOffset();
        int length = name != null ? name.length() : 0;
        linkedPositions.add(new LinkedPosition(name, offset, length));
    }

    public List<LinkedPosition> getLinkedPositions() {
        return linkedPositions;
    }

    public LinkedPositionResult getResult() {
        return new LinkedPositionResult(getTemplateContent(), linkedPositions);
    }

}

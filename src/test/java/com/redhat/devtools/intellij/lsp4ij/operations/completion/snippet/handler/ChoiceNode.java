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

import java.util.List;
import java.util.stream.Collectors;

public class ChoiceNode implements LspSnippetNode {

    private final Integer index;

    private final String name;

    private final List<String> choices;

    public ChoiceNode(Integer index, String name, List<String> choices) {
        this.index = index;
        this.name = name;
        this.choices = choices;
    }

    public Integer getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public List<String> getChoices() {
        return choices;
    }

    @Override
    public String toString() {
        return "choice(" + index + "," + name + ",[" + choices.stream().collect(Collectors.joining(",")) + "])";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((choices == null) ? 0 : choices.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ChoiceNode other = (ChoiceNode) obj;
        if (choices == null) {
            if (other.choices != null)
                return false;
        } else if (!choices.equals(other.choices))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

}

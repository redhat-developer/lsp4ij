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

public class LinkedPosition {

    private final String name;

    private final int offset;

    private final int length;

    public LinkedPosition(String name, int offset, int length) {
        this.name = name;
        this.offset = offset;
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }


    @Override
    public String toString() {
        return "position(" + getName() + "," + getOffset() + "," + getLength() + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + length;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + offset;
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
        LinkedPosition other = (LinkedPosition) obj;
        if (length != other.length)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (offset != other.offset)
            return false;
        return true;
    }


}
/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.extractors;

/**
 * Static segment.
 */
public class StaticSegment extends Segment {

    public StaticSegment(String value) {
        super(value);
    }

    @Override
    protected String matches(String input) {
        String staticValue = getValue();
        if (input.startsWith(staticValue)) {
            return staticValue;
        }
        return null;
    }

    @Override
    protected boolean isDynamic() {
        return false;
    }

    @Override
    public String toString() {
        return "static(\"" + getValue() + "\")";
    }

}

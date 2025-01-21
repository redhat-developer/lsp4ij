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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dynamic segment identified with ${...}.
 */
public class DynamicSegment extends Segment {

    public enum DynamicSegmentType {
        ADDRESS, PORT;
    }

    private Pattern pattern;

    private String regexp;

    private DynamicSegmentType type;

    public DynamicSegment(String value, String regexp, DynamicSegmentType type) {
        super(value);
        this.regexp = regexp;
        this.type = type;
    }

    @Override
    protected String matches(String input) {
        if (pattern == null) {
            pattern = Pattern.compile(regexp);
        }
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public DynamicSegmentType getType() {
        return type;
    }


    @Override
    protected boolean isDynamic() {
        return true;
    }

    @Override
    public String toString() {
        return "dynamic(\"" + getValue() + "\",\"" + regexp + "\")";
    }

}

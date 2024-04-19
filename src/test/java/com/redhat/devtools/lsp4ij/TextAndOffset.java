/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

/**
 * Parse content/offset which uses '|' to mark an offset.
 */
public class TextAndOffset {

    private final int offset;
    private final String content;

    public TextAndOffset(String contentWithOffset) {
        this.offset = contentWithOffset.indexOf('|');
        this.content = contentWithOffset.substring(0, offset) + contentWithOffset.substring(offset + 1);
    }

    public int getOffset() {
        return offset;
    }

    public String getContent() {
        return content;
    }
}

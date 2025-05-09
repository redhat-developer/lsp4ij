/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.uri;

import org.junit.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UriFormatterTest {

    /**
     * @See <a href="https://github.com/microsoft/vscode-uri/blob/edfdccd976efaf4bb8fdeca87e97c47257721729/src/test/uri.test.ts#L18C5-L23C8">uri.test.ts#L18C5-L23C8</a>
     * @throws Exception
     */
    @Test
    public void testFileToString() throws Exception {
        assertEquals("file:///c%3A/win/path", UriFormatter.asFormatted(new URI("file", "", "/c:/win/path", null), false));
        assertEquals("file:///c%3A/win/path", UriFormatter.asFormatted(new URI("file", "", "/C:/win/path", null), false));
    }
}

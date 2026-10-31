/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.uri;

import org.junit.Test;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WslUriConverterTest {

    private final WslUriConverter converter = new WslUriConverter();

    @Test
    public void toUri_wslLocalhost() {
        File file = new File("\\\\wsl.localhost\\Ubuntu-22.04\\home\\user\\file.txt");
        URI uri = converter.toUri(file);
        assertNotNull(uri);
        assertEquals("file://wsl.localhost/Ubuntu-22.04/home/user/file.txt", uri.toString());
    }

    @Test
    public void toUri_wslDollar() {
        File file = new File("\\\\wsl$\\Ubuntu\\home\\user\\file.txt");
        URI uri = converter.toUri(file);
        assertNotNull(uri);
        assertEquals("file://wsl$/Ubuntu/home/user/file.txt", uri.toString());
    }

    /**
     * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1794">Issue #1794</a>
     */
    @Test
    public void toUri_nonAsciiCharacters() {
        File file = new File("\\\\wsl.localhost\\Ubuntu-22.04\\home\\user\\projects\\Промпт для пересказа.md");
        URI uri = converter.toUri(file);
        assertNotNull(uri);
        assertEquals("file", uri.getScheme());
        assertEquals("wsl.localhost", uri.getAuthority());
        assertEquals("/Ubuntu-22.04/home/user/projects/Промпт для пересказа.md", uri.getPath());
    }

    @Test
    public void toUri_nonWslPath() {
        File file = new File("C:\\Users\\user\\file.txt");
        URI uri = converter.toUri(file);
        assertNull(uri);
    }
}

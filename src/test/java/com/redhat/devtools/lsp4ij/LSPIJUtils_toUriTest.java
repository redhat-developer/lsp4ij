/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * SAP SE - additional test cases
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link LSPIJUtils#toUri(File)} method.
 */
public class LSPIJUtils_toUriTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithWindowsPaths() {
        // Local path
        URI localUri = LSPIJUtils.toUri(new File("C:\\Users\\user\\file.txt"));
        assertEquals("file:///C:/Users/user/file.txt", localUri.toString());
        
        // Local path with spaces
        URI spacesUri = LSPIJUtils.toUri(new File("C:\\Users\\user name\\file.txt"));
        assertEquals("/C:/Users/user%20name/file.txt", spacesUri.getRawPath());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testToUriWithUnixPaths() {
        // Local path
        URI localUri = LSPIJUtils.toUri(new File("/home/user/file.txt"));
        assertEquals("file:///home/user/file.txt", localUri.toString());
        
        // Local path with spaces
        URI spacesUri = LSPIJUtils.toUri(new File("/home/user name/file.txt"));
        assertEquals("/home/user%20name/file.txt", spacesUri.getRawPath());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithUncPaths() {
        // Regular UNC path: \\server\share\...
        URI uncUri = LSPIJUtils.toUri(new File("\\\\server\\share\\folder\\file.txt"));
        assertEquals("file://server/share/folder/file.txt", uncUri.toString());
        assertEquals("server", uncUri.getAuthority());
        assertEquals("/share/folder/file.txt", uncUri.getPath());
        
        // WSL UNC path with $ character: \\wsl$\Ubuntu\...
        URI wslUri = LSPIJUtils.toUri(new File("\\\\wsl$\\Ubuntu\\home\\user\\file.txt"));
        assertEquals("file://wsl$/Ubuntu/home/user/file.txt", wslUri.toString());
        assertEquals("wsl$", wslUri.getAuthority());
        assertEquals("/Ubuntu/home/user/file.txt", wslUri.getPath());
        
        // Malformed UNC path (no backslash separator): \\server.txt
        URI malformedUri = LSPIJUtils.toUri(new File("\\\\server.txt"));
        assertEquals("file:////server.txt", malformedUri.toString());
    }
}

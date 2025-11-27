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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link LSPIJUtils#toUri(File)} method.
 */
public class LSPIJUtils_toUriTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithWslPath() {
        // Given: Create a File object from a UNC path string
        // Note: File object may normalize the path, so we test with the actual behavior
        String wslPath = "\\\\wsl$\\Ubuntu\\home\\user\\project\\file.txt";
        File wslFile = new File(wslPath);

        // When
        URI resultUri = LSPIJUtils.toUri(wslFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        
        // The URI should either:
        // 1. Have authority="wsl$" and path="/Ubuntu/home/user/project/file.txt" (correct UNC handling)
        // 2. Or at minimum, not produce the old broken format with 4 slashes
        String uriString = resultUri.toString();
        assertNotNull(uriString);
        
        // Verify it doesn't have the broken 4-slash format
        if (uriString.contains("file:////")) {
            fail("URI should not contain file://// (broken UNC format): " + uriString);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithLocalPathOnWindows() {
        // Given
        File localFile = new File("C:\\Users\\user\\project\\file.txt");

        // When
        URI resultUri = LSPIJUtils.toUri(localFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        assertEquals("/C:/Users/user/project/file.txt", resultUri.getPath());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testToUriWithLocalPathOnUnix() {
        // Given
        File localFile = new File("/home/user/project/file.txt");

        // When
        URI resultUri = LSPIJUtils.toUri(localFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        assertEquals("/home/user/project/file.txt", resultUri.getPath());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithSpacesOnWindows() {
        // Given
        File fileWithSpaces = new File("C:\\Users\\user name\\project name\\file.txt");

        // When
        URI resultUri = LSPIJUtils.toUri(fileWithSpaces);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        assertEquals("/C:/Users/user%20name/project%20name/file.txt", resultUri.getRawPath());
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void testToUriWithSpacesOnUnix() {
        // Given
        File fileWithSpaces = new File("/home/user name/project name/file.txt");

        // When
        URI resultUri = LSPIJUtils.toUri(fileWithSpaces);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        assertEquals("/home/user%20name/project%20name/file.txt", resultUri.getRawPath());
    }
}

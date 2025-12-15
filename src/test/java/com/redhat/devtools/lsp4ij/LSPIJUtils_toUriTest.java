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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Tests for {@link LSPIJUtils#toUri(File)} method.
 */
public class LSPIJUtils_toUriTest extends BasePlatformTestCase {

    public void testToUriWithLocalPaths() {
        if (SystemInfo.isWindows) {
            // Local path
            URI localUri = LSPIJUtils.toUri(new File("C:\\Users\\user\\file.txt"));
            assertEquals("file:///C:/Users/user/file.txt", localUri.toString());
            
            // Local path with spaces
            URI spacesUri = LSPIJUtils.toUri(new File("C:\\Users\\user name\\file.txt"));
            assertEquals("/C:/Users/user%20name/file.txt", spacesUri.getRawPath());
        } else {
            // Local path
            URI localUri = LSPIJUtils.toUri(new File("/home/user/file.txt"));
            assertEquals("file:///home/user/file.txt", localUri.toString());
            
            // Local path with spaces
            URI spacesUri = LSPIJUtils.toUri(new File("/home/user name/file.txt"));
            assertEquals("/home/user%20name/file.txt", spacesUri.getRawPath());
        }
    }

    public void testToUriWithUncPaths() {
        if (SystemInfo.isWindows) {
            // WSL UNC path with $ character
            URI wslUri = LSPIJUtils.toUri(new File("\\\\wsl$\\Ubuntu\\home\\user\\file.txt"));
            assertEquals("file://wsl$/Ubuntu/home/user/file.txt", wslUri.toString());
            assertEquals("wsl$", wslUri.getAuthority());
            assertEquals("/Ubuntu/home/user/file.txt", wslUri.getPath());
            
            // WSL UNC path with wsl.localhost
            URI wslLocalhostUri = LSPIJUtils.toUri(new File("\\\\wsl.localhost\\Ubuntu\\home\\user\\file.txt"));
            assertEquals("file://wsl.localhost/Ubuntu/home/user/file.txt", wslLocalhostUri.toString());
            assertEquals("wsl.localhost", wslLocalhostUri.getAuthority());
            assertEquals("/Ubuntu/home/user/file.txt", wslLocalhostUri.getPath());
            
            // Regular UNC path (not WSL): should use standard file URI
            URI uncUri = LSPIJUtils.toUri(new File("\\\\server\\share\\folder\\file.txt"));
            assertEquals("file", uncUri.getScheme());

            // Malformed UNC path (no backslash separator)
            URI malformedUri = LSPIJUtils.toUri(new File("\\\\server.txt"));
            assertEquals("file:////server.txt", malformedUri.toString());
        }
    }

    public void testUriToPathAssumptions() {
        if (SystemInfo.isWindows) {
            try {
                // This verifies the assumptions in the URI-to-path conversion in LSPIJUtils#findResourceFor(String)
                String wslUri = "file://wsl$/Ubuntu/home/user/file.txt";
                URI parsed = new URI(wslUri);
                String authority = parsed.getAuthority();
                String path = parsed.getPath();

                assertEquals("wsl$", authority);
                assertEquals("/Ubuntu/home/user/file.txt", path);
            } catch (URISyntaxException e) {
                throw new RuntimeException("URI parsing failed", e);
            }
        }
    }
}

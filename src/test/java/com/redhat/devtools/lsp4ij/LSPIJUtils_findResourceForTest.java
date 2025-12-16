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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests for {@link LSPIJUtils#findResourceFor(String)}}.
 */
public class LSPIJUtils_findResourceForTest extends BasePlatformTestCase {

    public void testSimpleUri() throws IOException {
        assertVirtualFileExists("dir", "file.txt", getUri("dir/file.txt"));
    }

    public void testEncodedUri() throws IOException {
        assertVirtualFileExists("dir", "file.txt", getUri("dir/file.txt", true));
        assertVirtualFileExists("base.dir", "file.txt", getUri("base.dir/file.txt", true));
    }

    public void testUncUriParsing() {
        // WSL paths should be parsed (but return null since the paths don't exist)
        Assert.assertNull(LSPIJUtils.findResourceFor("file://wsl$/Ubuntu/home/user4270d2e/test.txt"));
        Assert.assertNull(LSPIJUtils.findResourceFor("file://wsl.localhost/Ubuntu/home/user4270d2e/test.txt"));
        // Non-WSL UNC paths should not use special handling
        Assert.assertNull(LSPIJUtils.findResourceFor("file://server42cc119/share/folder/file.txt"));
    }

    private static @NotNull String getUri(String s) {
        return getUri(s, false);
    }

    private static @NotNull String getUri(String s, boolean encodeDriver) {
        File file = new File(System.getProperty("java.io.tmpdir"), s);
        String uri = LSPIJUtils.toUri(file).toASCIIString();
        if(encodeDriver) {
            return uri.replace("C:", "c%3A");
        }
        return uri;
    }

    private static void assertVirtualFileExists(@NotNull String baseDir,
                                                @NotNull String fileName,
                                                @NotNull String uri) throws IOException {
        assertVirtualFileExists(baseDir, fileName, uri, true);
    }

    private static void assertVirtualFileExists(@NotNull String baseDir,
                                                @NotNull String fileName,
                                                @NotNull String uri,
                                                boolean create) throws IOException {
        if(create) {
            createTempFileIfNeeded(baseDir, fileName);
        }
        VirtualFile file = LSPIJUtils.findResourceFor(uri);
        Assert.assertNotNull("Cannot find file with the uri '" + uri  + "'", file);
        file.refresh(true, true);
        Assert.assertTrue("File with the uri '" + uri  + "' doesn't exist", file.exists());
    }


    private static void createTempFileIfNeeded(String baseDir, String fileName) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        Path tempFile = Paths.get(tempDir, baseDir, fileName);
        if (Files.notExists(tempFile)) {
            Path dir = tempFile.getParent();
            if(Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
            Files.createFile(tempFile);
        }
    }

}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.uri;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * {@link JarUriConverter} finds the file that {@code new URL(uri)}, which it no longer uses, found, also for a URI with
 * characters that {@link URI} rejects, such as spaces.
 */
public class JarUriConverterTest extends BasePlatformTestCase {

    private final JarUriConverter converter = new JarUriConverter();

    public void testAJarUriWithCharactersThatUriRejectsFindsTheEntry() throws Exception {
        Path folder = Files.createDirectories(Files.createTempDirectory("lsp4ij-jar-uri").resolve("a b {c}^d`e%zz[f]"));
        Path jar = folder.resolve("some lib.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("pkg/Some File.txt"));
            out.write("some content".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar));
        String path = FileUtil.toSystemIndependentName(jar.toString());
        String unencoded = "jar:file:" + (path.startsWith("/") ? "" : "/") + path + "!/pkg/Some File.txt";
        String encoded = "jar:" + jar.toUri().toASCIIString() + "!/pkg/Some%20File.txt";

        VirtualFile entry = converter.findResourceFor(unencoded);

        assertNotNull(entry);
        assertEquals("some content", VfsUtilCore.loadText(entry));
        assertEquals(entry, converter.findResourceFor(encoded));
        assertEquals(entry, findFileWithNewUrl(unencoded));
    }

    public void testAUriGivesTheVfsUrlThatNewUrlGave() throws Exception {
        for (String uri : List.of(
                // Spaces
                "jar:file:/tmp/a b/some lib.jar!/pkg/Some File.class",
                "jar:file:///C:/Users/John Doe/.m2/repository/lib.jar!/pkg/Foo.class",
                // Encoded, partly or not
                "jar:file:/tmp/a%20b/lib.jar!/pkg/Foo.class",
                "jar:file:/tmp/a%20b c/lib.jar!/pkg/Foo.class",
                // The other ASCII characters that URI rejects
                "jar:file:/tmp/{a}^b`c|d\"e<f>/lib.jar!/pkg/Foo.class",
                "jar:file:/tmp/a\\b/lib.jar!/pkg\\Foo.class",
                // A % that starts no escape
                "jar:file:/tmp/100%zz/lib.jar!/pkg/50%.txt",
                // A no-break space, a tab, and letters beyond ASCII
                "jar:file:/tmp/a\u00A0b\tc/lib.jar!/pkg/Foo.class",
                "jar:file:/tmp/é ü/lib.jar!/pkg/Foo.class",
                // A fragment with a #, and whitespace at the end
                "jar:file:/tmp/a b/lib.jar!/pkg/Foo.class#a b#c",
                "jar:file:/tmp/a b/lib.jar!/pkg/Foo.class \n",
                // jrt: URIs, whose path can't hold [ and ]
                "jrt:/java.base/java/lang/String.class",
                "jrt:/Program Files/jdk [21]!/java.base/java/lang/String.class")) {
            assertEquals(uri, VfsUtilCore.convertFromUrl(newUrl(uri)), VfsUtilCore.convertFromUrl(JarUriConverter.toURL(uri)));
        }
    }

    public void testAJarUriWithoutAnEntryFindsNothing() {
        assertNull(converter.findResourceFor("jar:file:/tmp/a b/lib.jar"));
        assertNull(converter.findResourceFor("jar:"));
    }

    private static VirtualFile findFileWithNewUrl(@NotNull String uri) throws MalformedURLException {
        return VfsUtil.findFileByURL(newUrl(uri));
    }

    /**
     * Returns {@code new URL(uri)}, what {@link JarUriConverter} used before.
     */
    @SuppressWarnings("deprecation")
    private static @NotNull URL newUrl(@NotNull String uri) throws MalformedURLException {
        return new URL(uri);
    }
}

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

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

/**
 * {@link UriConverter} implementation dedicated to {@code jar:} and {@code jrt:} URIs.
 *
 * <p>
 * This converter is responsible for handling resources located inside:
 * <ul>
 *   <li>JAR files ({@code jar:file://...!/path})</li>
 *   <li>Java runtime images ({@code jrt:/modules/...})</li>
 * </ul>
 *
 * <p>
 * These URI types are commonly used by language servers when referring to:
 * <ul>
 *   <li>dependencies from Maven/Gradle caches</li>
 *   <li>JDK classes (via the {@code jrt} file system)</li>
 * </ul>
 *
 * <p>
 * This converter does <strong>not</strong> handle regular {@code file://} URIs.
 * </p>
 */
class JarUriConverter implements UriConverter {

    /** IntelliJ file system protocol for JAR files */
    public static final String JAR_PROTOCOL = "jar";

    /** IntelliJ file system protocol for Java runtime image (JRT) */
    public static final String JRT_PROTOCOL = "jrt";

    /** URI scheme prefix for JAR resources */
    private static final String JAR_SCHEME = JAR_PROTOCOL + ":";

    /** URI scheme prefix for JRT resources */
    private static final String JRT_SCHEME = JRT_PROTOCOL + ":";

    /**
     * Resolves a {@link VirtualFile} from a {@code jar:} or {@code jrt:} URI string.
     *
     * <p>
     * Example:
     * <pre>
     * jar:file:///C:/Users/.m2/repository/.../artifact.jar!/com/example/Foo.class
     * </pre>
     * </p>
     *
     * @param uri the URI string to resolve
     * @return the corresponding {@link VirtualFile}, or {@code null} if the URI
     *         is not a JAR/JRT URI or cannot be resolved
     */
    @Override
    public @Nullable VirtualFile findResourceFor(@NotNull String uri) {
        if (uri.startsWith(JAR_SCHEME) || uri.startsWith(JRT_SCHEME)) {
            try {
                return VfsUtil.findFileByURL(new URL(uri));
            } catch (MalformedURLException e) {
                // Invalid URI format → resource cannot be resolved
                return null;
            }
        }
        return null;
    }

    /**
     * Converts a JAR or JRT {@link VirtualFile} into a {@link URI}.
     *
     * <p>
     * The {@link VirtualFile#getUrl()} value is converted into a {@link URL}
     * and then into a {@link URI}.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the corresponding {@link URI}, or {@code null} if the file
     *         does not belong to a JAR or JRT file system
     */
    @Override
    public @Nullable URI toUri(@NotNull VirtualFile file) {
        String protocol = file.getFileSystem().getProtocol();
        if (JAR_PROTOCOL.equals(protocol) || JRT_PROTOCOL.equals(protocol)) {
            try {
                return Objects
                        .requireNonNull(VfsUtilCore.convertToURL(file.getUrl()))
                        .toURI();
            } catch (URISyntaxException e) {
                // Ignore invalid URI conversion
            }
        }
        return null;
    }

    /**
     * Converts a {@link File} into a {@link URI}.
     *
     * <p>
     * This implementation does not support conversion from {@link File}
     * because JAR and JRT resources are not directly backed by {@link File}
     * instances.
     * </p>
     *
     * @param file the Java file
     * @return always {@code null}
     */
    @Override
    public @Nullable URI toUri(@NotNull File file) {
        return null;
    }

    /**
     * Converts a JAR or JRT {@link VirtualFile} into its URI string representation.
     *
     * <p>
     * The returned string is suitable for use in Language Server Protocol (LSP)
     * messages.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the URI string, or {@code null} if the file does not belong to
     *         a JAR or JRT file system
     */
    @Override
    public @Nullable String toString(@NotNull VirtualFile file) {
        String protocol = file.getFileSystem().getProtocol();
        if (JAR_PROTOCOL.equals(protocol) || JRT_PROTOCOL.equals(protocol)) {
            return Objects
                    .requireNonNull(VfsUtilCore.convertToURL(file.getUrl()))
                    .toExternalForm();
        }
        return null;
    }
}

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
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
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

    /** The characters other than ASCII letters and digits that {@link URI} allows unencoded in some part of a URI */
    private static final String URI_PUNCTUATION = "-_.!~*'();/?:@&=+$,[]";

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

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
                return VfsUtil.findFileByURL(toURL(uri));
            } catch (URISyntaxException | MalformedURLException e) {
                // Invalid URI format → resource cannot be resolved
                return null;
            }
        }
        return null;
    }

    /**
     * Returns the {@link URL} of a {@code jar:} or {@code jrt:} URI string, the one that
     * {@code new URL(uri)}, deprecated since Java 20, gave.
     *
     * <p>
     * {@link URI} rejects characters that {@code new URL(String)} accepted, such as spaces: a URI with
     * such characters is percent-encoded first, and {@link VfsUtil#findFileByURL(URL)} decodes them again,
     * so it finds the same file.
     * </p>
     *
     * @param uri the {@code jar:} or {@code jrt:} URI string
     * @return the URL of the URI
     * @throws URISyntaxException if the URI is invalid even once encoded
     * @throws MalformedURLException if the URI has no URL, such as a {@code jar:} URI without {@code !/}
     */
    static @NotNull URL toURL(@NotNull String uri) throws URISyntaxException, MalformedURLException {
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException e) {
            parsed = new URI(encodeIllegalCharacters(uri));
        }
        return parsed.toURL();
    }

    /**
     * Percent-encodes the characters of a URI string that {@link URI} rejects: those it allows nowhere,
     * a {@code %} that starts no escape, a {@code #} after the first, and the {@code [} and {@code ]} of
     * a {@code jrt:} URI, whose path can't hold them. Like {@code new URL(String)}, it drops the whitespace
     * and control characters at the ends.
     */
    private static @NotNull String encodeIllegalCharacters(@NotNull String uri) {
        String spec = uri.trim();
        boolean jrt = spec.startsWith(JRT_SCHEME);
        if (!jrt && File.separatorChar == '\\') {
            // As the file: URL inside a jar: URL did on Windows, where it reads a backslash as a slash
            spec = spec.replace('\\', '/');
        }
        var encoded = new StringBuilder(spec.length());
        boolean fragment = false;
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            if (c == '#' && !fragment) {
                fragment = true;
                encoded.append(c);
            } else if (isAllowedInUri(c, jrt) || c == '%' && isEscape(spec, i)) {
                encoded.append(c);
            } else {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append('%').append(HEX.toHexDigits(b));
                }
            }
        }
        return encoded.toString();
    }

    private static boolean isAllowedInUri(char c, boolean jrt) {
        if (c > 127) {
            // URI allows the characters beyond ASCII that are neither spaces nor control characters
            return !Character.isSpaceChar(c) && !Character.isISOControl(c);
        }
        if (jrt && (c == '[' || c == ']')) {
            return false;
        }
        return Character.isLetterOrDigit(c) || URI_PUNCTUATION.indexOf(c) >= 0;
    }

    private static boolean isEscape(@NotNull String uri, int percentIndex) {
        return percentIndex + 2 < uri.length()
                && HexFormat.isHexDigit(uri.charAt(percentIndex + 1))
                && HexFormat.isHexDigit(uri.charAt(percentIndex + 2));
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

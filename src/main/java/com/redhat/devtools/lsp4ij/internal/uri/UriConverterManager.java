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

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Central {@link UriConverter} dispatcher used by LSP4IJ.
 *
 * <p>
 * This manager delegates URI conversion operations to a chain of
 * specialized {@link UriConverter} implementations, selecting the
 * first one able to handle the request.
 * </p>
 *
 * <p>
 * It follows a <strong>chain-of-responsibility</strong> pattern, allowing
 * new URI types to be supported without modifying existing converters.
 * </p>
 *
 * <p>
 * This class is internal to LSP4IJ and exposed as a singleton.
 * </p>
 */
@ApiStatus.Internal
public class UriConverterManager implements UriConverter {

    /** Singleton instance */
    private static final UriConverterManager INSTANCE;

    static {
        INSTANCE = new UriConverterManager();
    }

    /**
     * Returns the singleton instance of the URI converter manager.
     *
     * @return the shared {@link UriConverterManager} instance
     */
    public static UriConverterManager getInstance() {
        return INSTANCE;
    }

    /** Ordered list of URI converters */
    private final List<UriConverter> uriConverters;

    /**
     * Creates the URI converter manager and initializes the converter chain.
     *
     * <p>
     * The order is significant:
     * <ol>
     *   <li>{@link JarUriConverter} – JAR and JRT resources</li>
     *   <li>{@link WslUriConverter} – WSL UNC paths</li>
     *   <li>{@link DefaultUriConverter} – standard file system</li>
     * </ol>
     * </p>
     */
    UriConverterManager() {
        uriConverters = List.of(
                new JarUriConverter(),
                new WslUriConverter(),
                new DefaultUriConverter()
        );
    }

    /**
     * Resolves a {@link VirtualFile} from a URI string.
     *
     * <p>
     * Some language servers send percent-encoded URIs that IntelliJ cannot
     * resolve directly (e.g. Windows drive letters such as {@code c%3A}).
     * </p>
     *
     * <p>
     * To work around this:
     * <ul>
     *   <li>{@code +} characters are preserved</li>
     *   <li>the URI is URL-decoded before resolution</li>
     * </ul>
     * </p>
     *
     * @param uri the URI string
     * @return the resolved {@link VirtualFile}, or {@code null} if no converter
     *         can handle the URI
     */
    @Override
    public @Nullable VirtualFile findResourceFor(@NotNull String uri) {
        if (uri.contains("%")) {
            // Example:
            // file:///c%3A/Users/azerr/IdeaProjects/untitled7/test.js
            //
            // IntelliJ cannot resolve percent-encoded drive letters,
            // so the URI must be decoded first.

            // Preserve '+' characters during decoding
            uri = uri.replace("+", "%2B");

            // Decode percent-encoded characters
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        }

        for (UriConverter uriConverter : uriConverters) {
            VirtualFile file = uriConverter.findResourceFor(uri);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    /**
     * Converts an IntelliJ {@link VirtualFile} into a {@link URI}.
     *
     * <p>
     * The first converter in the chain capable of handling the file
     * is used.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the corresponding {@link URI}, or {@code null} if unsupported
     */
    @Override
    public @Nullable URI toUri(@NotNull VirtualFile file) {
        for (UriConverter uriConverter : uriConverters) {
            URI uri = uriConverter.toUri(file);
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }

    /**
     * Converts a Java {@link File} into a {@link URI}.
     *
     * <p>
     * This is primarily used when sending file references to a language server.
     * </p>
     *
     * @param file the Java file
     * @return the corresponding {@link URI}, or {@code null} if unsupported
     */
    @Override
    public @Nullable URI toUri(@NotNull File file) {
        for (UriConverter uriConverter : uriConverters) {
            URI uri = uriConverter.toUri(file);
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }

    /**
     * Converts an IntelliJ {@link VirtualFile} into its URI string representation.
     *
     * <p>
     * This is typically used when serializing URIs in LSP messages.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the URI string, or {@code null} if unsupported
     */
    @Override
    public @Nullable String toString(@NotNull VirtualFile file) {
        for (UriConverter uriConverter : uriConverters) {
            String uri = uriConverter.toString(file);
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }
}

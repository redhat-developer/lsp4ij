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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;

/**
 * Responsible for converting between:
 * <ul>
 *   <li>LSP-style URIs (file://, jar://, etc.)</li>
 *   <li>IntelliJ {@link VirtualFile}</li>
 *   <li>Java {@link File}</li>
 * </ul>
 *
 * <p>
 * This abstraction centralizes all URI ↔ file conversions needed by LSP4IJ,
 * including resolution of {@link VirtualFile} instances from URI strings
 * received from language servers.
 * </p>
 *
 * <p>
 * This interface is internal to LSP4IJ and is not intended for public API use.
 * </p>
 */
@ApiStatus.Internal
public interface UriConverter {

    /**
     * Resolves an IntelliJ {@link VirtualFile} from a URI string.
     *
     * <p>
     * The URI is typically an LSP URI (e.g. {@code file:///path/to/file},
     * {@code jar://...}, etc.).
     * </p>
     *
     * @param uri the URI string to resolve
     * @return the corresponding {@link VirtualFile}, or {@code null} if
     *         the resource cannot be resolved
     */
    @Nullable
    VirtualFile findResourceFor(@NotNull String uri);

    /**
     * Converts an IntelliJ {@link VirtualFile} to a {@link URI}.
     *
     * <p>
     * This default implementation first converts the {@link VirtualFile}
     * to a Java {@link File} using
     * {@link VfsUtilCore#virtualToIoFile(VirtualFile)}, then delegates
     * to {@link #toUri(File)}.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the corresponding {@link URI}, or {@code null} if the conversion fails
     */
    default @Nullable URI toUri(@NotNull VirtualFile file) {
        return toUri(VfsUtilCore.virtualToIoFile(file));
    }

    /**
     * Converts a Java {@link File} to a {@link URI}.
     *
     * <p>
     * Implementations may handle special cases such as:
     * <ul>
     *   <li>non-local file systems</li>
     *   <li>case normalization (important on Windows)</li>
     *   <li>symbolic links</li>
     * </ul>
     * </p>
     *
     * @param file the Java file
     * @return the corresponding {@link URI}, or {@code null} if it cannot be created
     */
    @Nullable
    URI toUri(@NotNull File file);

    /**
     * Converts an IntelliJ {@link VirtualFile} to its URI string representation.
     *
     * <p>
     * The returned string is expected to be compatible with the Language Server
     * Protocol (LSP), typically using {@code file://} URIs.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return the URI string, or {@code null} if the conversion fails
     */
    @Nullable
    String toString(@NotNull VirtualFile file);

}

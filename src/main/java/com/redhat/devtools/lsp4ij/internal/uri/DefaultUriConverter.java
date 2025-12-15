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
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Default {@link UriConverter} implementation for standard {@code file://} URIs.
 *
 * <p>
 * This converter is used for regular files located on the local file system.
 * It follows the Language Server Protocol (LSP) specification for file URIs
 * and ensures compatibility with IntelliJ's Virtual File System (VFS).
 * </p>
 */
class DefaultUriConverter implements UriConverter {

    /**
     * Resolves a {@link VirtualFile} from a {@code file://} URI string.
     *
     * <p>
     * The URI is first normalized using {@link VfsUtilCore#fixURLforIDEA(String)}
     * to handle platform-specific issues (e.g. Windows paths),
     * then resolved through IntelliJ's {@link VirtualFileManager}.
     * </p>
     *
     * @param uri the file URI string
     * @return the corresponding {@link VirtualFile}, or {@code null} if it cannot be resolved
     */
    @Override
    public @Nullable VirtualFile findResourceFor(@NotNull String uri) {
        return VirtualFileManager.getInstance()
                .findFileByUrl(VfsUtilCore.fixURLforIDEA(uri));
    }

    /**
     * Converts a Java {@link File} to an LSP-compliant {@link URI}.
     *
     * <p>
     * The Language Server Protocol requires {@code file://} URIs with
     * properly encoded paths and without host components.
     * </p>
     *
     * <p>
     * This implementation explicitly builds the URI to avoid
     * platform-specific issues, especially on Windows.
     * </p>
     *
     * @param file the Java file
     * @return the corresponding {@link URI}
     */
    @Override
    public @Nullable URI toUri(@NotNull File file) {
        try {
            // LSP-compliant file URI: file:///absolute/path
            return new URI(
                    "file",
                    "", // no authority
                    file.getAbsoluteFile().toURI().getPath(),
                    null
            );
        } catch (URISyntaxException e) {
            // Fallback to the standard Java URI representation
            return file.getAbsoluteFile().toURI();
        }
    }

    /**
     * Converts an IntelliJ {@link VirtualFile} to its {@code file://} URI string representation.
     *
     * <p>
     * This method is intentionally left unimplemented here and may be
     * provided by a higher-level or composite {@link UriConverter}.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return {@code null}
     */
    @Override
    public @Nullable String toString(@NotNull VirtualFile file) {
        return null;
    }
}

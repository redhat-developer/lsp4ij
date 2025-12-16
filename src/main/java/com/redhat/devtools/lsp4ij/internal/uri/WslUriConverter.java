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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * {@link UriConverter} implementation dedicated to Windows Subsystem for Linux (WSL)
 * paths exposed as Windows UNC paths.
 *
 * <p>
 * WSL file systems are typically accessible on Windows using UNC paths such as:
 * <pre>
 * \\wsl$\Ubuntu\home\\user\project\file.txt
 * </pre>
 *
 * <p>
 * Language servers expect these paths to be represented as valid {@code file://}
 * URIs with an authority component.
 * </p>
 */
class WslUriConverter implements UriConverter {

    private static final Logger LOGGER = Logger.getInstance(WslUriConverter.class);

    /**
     * Resolving a {@link VirtualFile} from a WSL URI.
     *
     * <p>
     * Handle WSL UNC paths ('\\wsl$\...' or '\\wsl.localhost\...') on Windows
     * Corresponding URIs have the format: 'file://wsl$/...' or 'file://wsl.localhost/...'
     * </p>
     *
     * @param uri the URI string
     * @return the virtual file
     */
    @Override
    public @Nullable VirtualFile findResourceFor(@NotNull String uri) {
        // Handle WSL UNC paths ('\\wsl$\...' or '\\wsl.localhost\...') on Windows
        // Corresponding URIs have the format: 'file://wsl$/...' or 'file://wsl.localhost/...'
        if (SystemInfo.isWindows && (uri.startsWith("file://wsl$/") || uri.startsWith("file://wsl.localhost/"))) {
            try {
                URI parsedUri = new URI(uri);
                String authority = parsedUri.getAuthority();
                String path = parsedUri.getPath();
                if (authority != null && path != null && authority.startsWith("wsl")) {
                    String uncPath = "\\\\" + authority + path.replace('/', '\\');
                    return LocalFileSystem.getInstance().findFileByPath(uncPath);
                }
            } catch (URISyntaxException e) {
                LOGGER.warn("Failed to parse WSL URI: " + uri, e);
                // Delegate to a more generic converter
            }
        }
        return null;
    }

    /**
     * Converts a Windows UNC path pointing to a WSL file into a {@code file://} URI.
     *
     * <p>
     * Example conversion:
     * <pre>
     * \\wsl$\Ubuntu\home\\user\file.txt
     * →
     * file://wsl$/Ubuntu/home/user/file.txt
     * </pre>
     *
     * <p>
     * A lenient URI construction is used to support the non-standard but commonly
     * used {@code wsl$} authority.
     * </p>
     *
     * @param file the Java file representing a WSL UNC path
     * @return the corresponding {@link URI}, or {@code null} if the file
     *         is not a WSL UNC path
     */
    @Override
    public @Nullable URI toUri(@NotNull File file) {
        String path = file.getPath();
        if (path.startsWith("\\\\wsl$\\") || path.startsWith("\\\\wsl.localhost\\")) {
            // Strip leading UNC prefix (\\)
            // Format: authority\path\...
            String uncPath = path.substring(2);

            // Extract authority and remaining path
            int firstSep = uncPath.indexOf('\\');
            if (firstSep > 0) {
                String authority = uncPath.substring(0, firstSep);
                String uriPath = uncPath.substring(firstSep).replace('\\', '/');

                // Use lenient parsing to allow WSL authority such as "wsl$"
                return URI.create("file://" + authority + uriPath);
            }
        }
        return null;
    }

    /**
     * Converts an IntelliJ {@link VirtualFile} to a URI string.
     *
     * <p>
     * This operation is not currently supported for WSL virtual files and is
     * expected to be handled by a higher-level converter if needed.
     * </p>
     *
     * @param file the IntelliJ virtual file
     * @return always {@code null}
     */
    @Override
    public @Nullable String toString(@NotNull VirtualFile file) {
        return null;
    }
}

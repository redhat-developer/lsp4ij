/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.internal.uri.UriConverterManager;
import com.redhat.devtools.lsp4ij.internal.uri.UriFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Provides a base implementation of the {@link FileUriSupport} interface
 * to handle conversions between {@link VirtualFile} and {@link URI},
 * including string representations adapted for Language Server Protocol clients.
 *
 * <p>This implementation supports optional URI encoding and treats special protocols
 * such as {@code jar} and {@code jrt} using their external form.</p>
 */
public class FileUriSupportBase implements FileUriSupport {

    @Override
    public @Nullable URI getFileUri(@NotNull VirtualFile file) {
        return LSPIJUtils.toUri(file);
    }

    @Override
    public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
        return LSPIJUtils.findResourceFor(fileUri);
    }

    @Override
    public String toString(@NotNull VirtualFile file) {
        String uriString = UriConverterManager.getInstance().toString(file);
        if (uriString != null) {
            return uriString;
        }
        URI fileUri = getFileUri(file);
        return fileUri != null ? toString(fileUri, file.isDirectory()) : null;
    }

    @Override
    public @Nullable String toString(@NotNull URI fileUri, boolean directory) {
        String uri = isEncoded() ? UriFormatter.asFormatted(fileUri, false) : fileUri.toASCIIString();
        if (directory) {
            // For directory case, remove last '/'
            char last = uri.charAt(uri.length() - 1);
            if (last == '/' || last == '\\') {
                return uri.substring(0, uri.length() - 1);
            }
        }
        return uri;
    }

    protected boolean isEncoded() {
        return false;
    }
}

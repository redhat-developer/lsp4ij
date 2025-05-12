/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * File Uri support.
 */
public interface FileUriSupport {

    public static final FileUriSupport DEFAULT = new FileUriSupportBase();

    public static final FileUriSupport ENCODED = new FileUriSupportBase() {

        @Override
        protected boolean isEncoded() {
            return true;
        }
    };

    /**
     * Returns the file Uri from the given virtual file and null otherwise.
     *
     * @param file the virtual file.
     * @return the file Uri from the given virtual file and null otherwise.
     */
    @Nullable
    URI getFileUri(@NotNull VirtualFile file);

    /**
     * Returns the virtual file found by the given file Uri and null otherwise.
     *
     * @param fileUri the file Uri.
     * @return the virtual file found by the given file Uri and null otherwise.
     */
    @Nullable
    VirtualFile findFileByUri(@NotNull String fileUri);

    /**
     * Converts the given {@link VirtualFile} to its URI string representation.
     *
     * @param file the virtual file to convert, must not be {@code null}
     * @return the URI string representation of the virtual file
     */
    String toString(@NotNull VirtualFile file);

    /**
     * Converts the given {@link URI} to its string representation, optionally treating it as a directory.
     *
     * @param fileUri the URI to convert, must not be {@code null}
     * @param directory {@code true} if the URI should be treated as a directory (typically affects the trailing slash),
     *                  {@code false} otherwise
     * @return the string representation of the URI, formatted accordingly
     */
    String toString(@NotNull URI fileUri, boolean directory);

    @Nullable
    public static URI getFileUri(@NotNull VirtualFile file,
                                 @Nullable FileUriSupport fileUriSupport) {
        URI fileUri = fileUriSupport != null ? fileUriSupport.getFileUri(file) : null;
        if (fileUri != null) {
            return fileUri;
        }
        return DEFAULT.getFileUri(file);
    }

    @Nullable
    public static VirtualFile findFileByUri(@NotNull String fileUri,
                                            @Nullable FileUriSupport fileUriSupport) {
        VirtualFile file = fileUriSupport != null ? fileUriSupport.findFileByUri(fileUri) : null;
        if (file != null) {
            return file;
        }
        return DEFAULT.findFileByUri(fileUri);
    }

    @Nullable
    public static String toString(@Nullable VirtualFile file,
                                  @Nullable FileUriSupport fileUriSupport) {
        if (file == null) {
            return null;
        }
        URI fileUri = getFileUri(file, fileUriSupport);
        if (fileUri == null) {
            return null;
        }
        return toString(fileUri, file.isDirectory(), fileUriSupport);
    }

    @Nullable
    public static String toString(@NotNull URI fileUri,
                                  @Nullable FileUriSupport fileUriSupport) {
        return toString(fileUri, false, fileUriSupport);
    }

    @Nullable
    public static String toString(@NotNull URI fileUri,
                                  boolean directory,
                                  @Nullable FileUriSupport fileUriSupport) {
        String uri = fileUriSupport != null ? fileUriSupport.toString(fileUri, directory) : null;
        if (uri != null) {
            return uri;
        }
        return DEFAULT.toString(fileUri, directory);
    }
}

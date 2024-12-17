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
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * File Uri support.
 */
public interface FileUriSupport {

    public static final FileUriSupport DEFAULT = new FileUriSupport() {

        @Override
        public @Nullable URI getFileUri(@NotNull VirtualFile file) {
            return LSPIJUtils.toUri(file);
        }

        @Override
        public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
            return LSPIJUtils.findResourceFor(fileUri);
        }
    };

    /**
     * Returns the file Uri from the given virtual file and null otherwise.
     * @param file the virtual file.
     * @return the file Uri from the given virtual file and null otherwise.
     */
    @Nullable
    URI getFileUri(@NotNull VirtualFile file);

    /**
     * Returns the virtual file found by the given file Uri and null otherwise.
     * @param fileUri the file Uri.
     * @return the virtual file found by the given file Uri and null otherwise.
     */
    @Nullable
    VirtualFile findFileByUri(@NotNull String fileUri);

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
}

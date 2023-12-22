/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition;

import com.intellij.openapi.fileTypes.FileType;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mapping between a given {@link FileType} and a given language server.
 */
public class ServerFileTypeMapping extends ServerMapping {

    @NotNull
    private final FileType fileType;

    public ServerFileTypeMapping(@NotNull FileType fileType, @NotNull String serverId, @Nullable String languageId, @NotNull DocumentMatcher documentMatcher) {
        super(serverId, languageId, documentMatcher);
        this.fileType = fileType;
    }

    public @NotNull FileType getFileType() {
        return fileType;
    }

}
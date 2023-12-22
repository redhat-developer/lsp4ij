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

import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for language servers mappings.
 */
public abstract class ServerMapping {

    @NotNull
    private final String serverId;
    @NotNull
    private final String languageId;
    @NotNull
    private final DocumentMatcher documentMatcher;

    public ServerMapping(@NotNull String serverId, @NotNull String languageId, @NotNull DocumentMatcher documentMatcher) {
        this.serverId = serverId;
        this.languageId = languageId;
        this.documentMatcher = documentMatcher;
    }

    @NotNull
    public String getServerId() {
        return serverId;
    }

    @NotNull
    public String getLanguageId() {
        return languageId;
    }

    @NotNull
    public DocumentMatcher getDocumentMatcher() {
        return documentMatcher;
    }

}
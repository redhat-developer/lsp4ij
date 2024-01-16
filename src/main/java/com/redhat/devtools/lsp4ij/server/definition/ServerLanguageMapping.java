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

import com.intellij.lang.Language;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Language mapping between a language server ID and an IntelliJ language.
 */
public class ServerLanguageMapping extends ServerMapping {

    @NotNull
    private final Language language;

    public ServerLanguageMapping(@NotNull Language language, @NotNull String serverId, @Nullable String languageId, @NotNull DocumentMatcher documentMatcher) {
        super(serverId, languageId, documentMatcher);
        this.language = language;
    }

    @NotNull
    public Language getLanguage() {
        return language;
    }

}

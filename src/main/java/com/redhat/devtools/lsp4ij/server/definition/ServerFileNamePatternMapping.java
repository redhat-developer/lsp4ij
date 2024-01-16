/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.List;

/**
 * Mapping between a given array of file name patterns and a given language server.
 */
public class ServerFileNamePatternMapping extends ServerMapping {

    @NotNull
    private final List<String> fileNamePatterns;
    private final List<FileNameMatcher> fileNameMatchers;

    public ServerFileNamePatternMapping(@NotNull List<String> fileNamePatterns, @NotNull String serverId, @Nullable String languageId, @NotNull DocumentMatcher documentMatcher) {
        super(serverId, languageId, documentMatcher);
        this.fileNamePatterns = fileNamePatterns;
        this.fileNameMatchers = createFileNameMatchers(fileNamePatterns);
    }

    private List<FileNameMatcher> createFileNameMatchers(List<String> fileNamePatterns) {
        return fileNamePatterns
                .stream()
                .map(pattern -> FileNameMatcherFactory.getInstance().createMatcher(pattern))
                .toList();
    }

    public @NotNull List<String> getFileNamePatterns() {
        return fileNamePatterns;
    }

    public List<FileNameMatcher> getFileNameMatchers() {
        return fileNameMatchers;
    }
}
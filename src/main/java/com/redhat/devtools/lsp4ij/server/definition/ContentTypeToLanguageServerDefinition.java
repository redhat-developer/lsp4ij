/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ContentTypeToLanguageServerDefinition {
    @Nullable
    private final Language language;

    @Nullable
    private final FileType fileType;

    @NotNull
    private final LanguageServerDefinition serverDefinition;

    @NotNull
    private final DocumentMatcher documentMatcher;

    public ContentTypeToLanguageServerDefinition(@NotNull Language language,
                                                 @NotNull LanguageServerDefinition serverDefinition,
                                                 @NotNull DocumentMatcher documentMatcher) {
        this.language = language;
        this.fileType = null;
        this.serverDefinition = serverDefinition;
        this.documentMatcher = documentMatcher;
    }

    public ContentTypeToLanguageServerDefinition(@NotNull FileType fileType,
                                                 @NotNull LanguageServerDefinition serverDefinition,
                                                 @NotNull DocumentMatcher documentMatcher) {
        this.language = null;
        this.fileType = fileType;
        this.serverDefinition = serverDefinition;
        this.documentMatcher = documentMatcher;
    }

    public boolean match(Language language, FileType fileType) {
        if (this.fileType != null) {
            return this.fileType.equals(fileType);
        }
        if (this.language == null || language == null) {
            return false;
        }
        return language.isKindOf(this.language);
    }

    public boolean match(VirtualFile file, Project project) {
        return getServerDefinition().supportsCurrentEditMode(project) && documentMatcher.match(file, project);
    }

    public boolean shouldBeMatchedAsynchronously(Project project) {
        return documentMatcher.shouldBeMatchedAsynchronously(project);
    }

    public boolean isEnabled() {
        return getServerDefinition().isEnabled();
    }

    public @NotNull <R> CompletableFuture<Boolean> matchAsync(VirtualFile file, Project project) {
        if (!getServerDefinition().supportsCurrentEditMode(project)) {
            return CompletableFuture.completedFuture(false);
        }
        return documentMatcher.matchAsync(file, project);
    }

    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    public @Nullable Language getLanguage() {
        return language;
    }

    public @Nullable FileType getFileType() {
        return fileType;
    }
}

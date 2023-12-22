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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;

public class ContentTypeToLanguageServerDefinition extends AbstractMap.SimpleEntry<Language, LanguageServerDefinition> {

    private final DocumentMatcher documentMatcher;

    public ContentTypeToLanguageServerDefinition(@NotNull Language language,
                                                 @NotNull LanguageServerDefinition provider,
                                                 @NotNull DocumentMatcher documentMatcher) {
        super(language, provider);
        this.documentMatcher = documentMatcher;
    }

    public boolean match(VirtualFile file, Project project) {
        return getValue().supportsCurrentEditMode(project) && documentMatcher.match(file, project);
    }

    public boolean shouldBeMatchedAsynchronously(Project project) {
        return documentMatcher.shouldBeMatchedAsynchronously(project);
    }

    public boolean isEnabled() {
        return getValue().isEnabled();
    }

    public @NotNull <R> CompletableFuture<Boolean> matchAsync(VirtualFile file, Project project) {
        if (!getValue().supportsCurrentEditMode(project)) {
            return CompletableFuture.completedFuture(false);
        }
        return documentMatcher.matchAsync(file, project);
    }
}

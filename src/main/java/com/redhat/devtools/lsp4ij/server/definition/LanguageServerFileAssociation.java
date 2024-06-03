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

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.AbstractDocumentMatcher;
import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Language server file association is computed from defined language, file type, file name pattern mappings comming from:
 *
 * <ul>
 *     <li>settings</li>
 *     <li>extension point</li>
 * </ul>
 * <p>
 * This class stores the real {@link Language},{@link FileType} or list of {@link FileNameMatcher} and
 * it is used to match if a given file must be associated with a given language server definition.
 */
public class LanguageServerFileAssociation {
    @Nullable
    private final Language language;

    @Nullable
    private final FileType fileType;

    @Nullable
    private final List<FileNameMatcher> fileNameMatchers;

    @NotNull
    private final LanguageServerDefinition serverDefinition;

    @NotNull
    private final DocumentMatcher documentMatcher;

    @Nullable
    private final String languageId;

    public LanguageServerFileAssociation(@NotNull Language language,
                                         @NotNull LanguageServerDefinition serverDefinition,
                                         @NotNull DocumentMatcher documentMatcher,
                                         @Nullable String languageId) {
        this.language = language;
        this.fileType = null;
        this.fileNameMatchers = null;
        this.serverDefinition = serverDefinition;
        this.documentMatcher = documentMatcher;
        this.languageId = languageId;
    }

    public LanguageServerFileAssociation(@NotNull FileType fileType,
                                         @NotNull LanguageServerDefinition serverDefinition,
                                         @NotNull DocumentMatcher documentMatcher,
                                         @Nullable String languageId) {
        this.language = null;
        this.fileType = fileType;
        this.fileNameMatchers = null;
        this.serverDefinition = serverDefinition;
        this.documentMatcher = documentMatcher;
        this.languageId = languageId;
    }


    public LanguageServerFileAssociation(@NotNull List<FileNameMatcher> fileNameMatchers,
                                         @NotNull LanguageServerDefinition serverDefinition,
                                         @NotNull DocumentMatcher documentMatcher,
                                         @Nullable String languageId) {
        this.language = null;
        this.fileType = null;
        this.fileNameMatchers = fileNameMatchers;
        this.serverDefinition = serverDefinition;
        this.documentMatcher = documentMatcher;
        this.languageId = languageId;
    }

    /**
     * Returns true if the given language, file type or file matches the server definition and false otherwise.
     *
     * <p>
     * This method is the first level to check if a given file must be associated with the language server.
     * The match is very fast.
     * </p>
     *
     * @param language the language to match.
     * @param fileType the file type to match.
     * @param filename the file name to match by using file name patterns.
     * @return true if the given language, file type or file matches the server definition and false otherwise.
     */
    public boolean match(@Nullable Language language, @Nullable FileType fileType, @NotNull String filename) {
        if (this.fileType != null) {
            return this.fileType.equals(fileType);
        }
        if (this.language != null & language != null) {
            return language.isKindOf(this.language);
        }
        if (fileNameMatchers != null) {
            for (var matcher : fileNameMatchers) {
                if (matcher.acceptsCharSequence(filename)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given file matches the server definition and false otherwise.
     *
     * <p>
     * This method is the second level to check if a given file must be associated with the language server and it is executed in non blocking read action if {@link #shouldBeMatchedAsynchronously(Project)}
     * return true.
     * It can take some times (ex: check if the project of the file contains some Java class in the classpath)
     * </p>
     *
     * @param file    the
     * @param project
     * @return
     */
    public boolean match(VirtualFile file, Project project) {
        return getServerDefinition().supportsCurrentEditMode(project) && documentMatcher.match(file, project);
    }

    /**
     * Returns true if the match must be done asynchronously and false otherwise.
     * <p>
     * A typical usecase is when IJ is indexing or read action is not allowed,this method should return true, to execute match in a non blocking read action.
     *
     * @param project the project.
     * @return true if the match must be done asynchronously and false otherwise.
     * @see AbstractDocumentMatcher
     */
    public boolean shouldBeMatchedAsynchronously(Project project) {
        return documentMatcher.shouldBeMatchedAsynchronously(project);
    }

    /**
     * Returns true if the file association is enabled and false otherwise.
     *
     * @return true if the file association is enabled and false otherwise.
     */
    public boolean isEnabled(Project project) {
        return getServerDefinition().isEnabled(project);
    }

    public @NotNull <R> CompletableFuture<Boolean> matchAsync(VirtualFile file, Project project) {
        if (!getServerDefinition().supportsCurrentEditMode(project)) {
            return CompletableFuture.completedFuture(false);
        }
        return documentMatcher.matchAsync(file, project);
    }

    /**
     * Returns the language server definition.
     *
     * @return the language server definition.
     */
    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    /**
     * Returns the language and null otherwise.
     *
     * @return the language and null otherwise.
     */
    public @Nullable Language getLanguage() {
        return language;
    }

    /**
     * Returns the file type and null otherwise.
     *
     * @return the file type and null otherwise.
     */
    public @Nullable FileType getFileType() {
        return fileType;
    }

    /**
     * Returns the list of file name matcher and null otherwise.
     *
     * @return the list of file name matcher and null otherwise.
     */
    public List<FileNameMatcher> getFileNameMatchers() {
        return fileNameMatchers;
    }

    /**
     * Returns the LSP language id and null otherwise.
     *
     * @return the LSP language id and null otherwise.
     */
    public @Nullable String getLanguageId() {
        return languageId;
    }
}

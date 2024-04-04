/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files.operations;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.features.files.operations.executors.WillCreateFilesExecutor;
import com.redhat.devtools.lsp4ij.features.files.operations.executors.WillDeleteFilesExecutor;
import com.redhat.devtools.lsp4ij.features.files.operations.executors.WillRenameFilesExecutor;
import com.redhat.devtools.lsp4ij.settings.RefactoringOnFileOperationsKind;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * File operation manager.
 */
public class FileOperationsManager {

    private final LanguageServerWrapper languageServerWrapper;
    @Nullable
    private ServerCapabilities serverCapabilities;

    @NotNull
    private final Map<FileOperationOptions, FileOperationMatcher> matchers;

    @NotNull
    private final WillCreateFilesExecutor willCreateFilesExecutor;
    @NotNull
    private final WillDeleteFilesExecutor willDeleteFilesExecutor;
    @NotNull
    private final WillRenameFilesExecutor willRenameFilesExecutor;

    public FileOperationsManager(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
        this.matchers = new HashMap<>();
        this.willCreateFilesExecutor = new WillCreateFilesExecutor(this);
        this.willDeleteFilesExecutor = new WillDeleteFilesExecutor(this);
        this.willRenameFilesExecutor = new WillRenameFilesExecutor(this);
    }

    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        this.matchers.clear();
    }

    // ----------------- Create files operations

    /**
     * Returns true if the given file uri can send a 'workspace/willCreateFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/willCreateFiles' notification and false otherwise.
     */
    public boolean canWillCreateFiles(@NotNull URI fileUri,
                                     boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillCreate());
    }

    /**
     * Returns true if the given file uri can send a 'workspace/didCreateFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/didCreateFiles' notification and false otherwise.
     */
    public boolean canDidCreateFiles(@NotNull URI fileUri,
                                     boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidCreate());
    }

    // ----------------- Delete files operations

    /**
     * Returns true if the given file uri can send a 'workspace/willDeleteFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/willDeleteFiles' notification and false otherwise.
     */
    public boolean canWillDeleteFiles(@NotNull URI fileUri,
                                      boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillDelete());
    }

    /**
     * Returns true if the given file uri can send a 'workspace/didDeleteFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/didDeleteFiles' notification and false otherwise.
     */
    public boolean canDidDeleteFiles(@NotNull URI fileUri,
                                     boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidDelete());
    }

    // ----------------- Rename files operations

    /**
     * Returns true if the given file uri can send a 'workspace/willRenameFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/willRenameFiles' notification and false otherwise.
     */
    public boolean canWillRenameFiles(@NotNull URI fileUri,
                                      boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillRename());
    }

    /**
     * Returns true if the given file uri can send a 'workspace/didRenameFiles' notification and false otherwise.
     *
     * @param fileUri  the renamed file uri.
     * @param isFolder true if it is a folder and false otherwise.
     * @return true if the given file uri can send a 'workspace/didRenameFiles' notification and false otherwise.
     */
    public boolean canDidRenameFiles(@NotNull URI fileUri,
                                     boolean isFolder) {
        if (UserDefinedLanguageServerSettings.getInstance(getProject()).getRefactoringOnFileOperationsKind() == RefactoringOnFileOperationsKind.skip) {
            return false;
        }
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidRename());
    }

    private boolean canSupportFileOperation(@NotNull URI fileUri,
                                            boolean isFolder,
                                            @Nullable FileOperationOptions fileOperationOptions) {
        if (fileOperationOptions == null || fileOperationOptions.getFilters() == null || fileOperationOptions.getFilters().isEmpty()) {
            return false;
        }
        return getMatcher(fileOperationOptions).isMatch(fileUri, isFolder);
    }

    @NotNull
    private FileOperationMatcher getMatcher(FileOperationOptions fileOperationOptions) {
        var matcher = matchers.get(fileOperationOptions);
        if (matcher != null) {
            return matcher;
        }
        synchronized (matchers) {
            matchers.putIfAbsent(fileOperationOptions, new FileOperationMatcher((fileOperationOptions.getFilters())));
        }
        return matchers.get(fileOperationOptions);
    }

    private FileOperationsServerCapabilities getFileOperationsServerCapabilities() {
        if (serverCapabilities != null && serverCapabilities.getWorkspace() != null) {
            return serverCapabilities.getWorkspace().getFileOperations();
        }
        return null;
    }

    public LanguageServerWrapper getLanguageServerWrapper() {
        return languageServerWrapper;
    }

    public @NotNull WillCreateFilesExecutor getWillCreateFilesExecutor() {
        return willCreateFilesExecutor;
    }

    public @NotNull WillDeleteFilesExecutor getWillDeleteFilesExecutor() {
        return willDeleteFilesExecutor;
    }

    public @NotNull WillRenameFilesExecutor getWillRenameFilesExecutor() {
        return willRenameFilesExecutor;
    }

    private Project getProject() {
        return languageServerWrapper.getProject();
    }

}

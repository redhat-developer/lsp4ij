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

import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages support for file operations (create, delete, rename) declared
 * in a Language Server's {@link FileOperationsServerCapabilities}.
 * <p>
 * The {@code FileOperationsManager} determines, for a given URI and context,
 * whether the corresponding LSP notifications can be sent to the server.
 * It supports the following operations:
 * <ul>
 *     <li>{@code workspace/willCreateFiles} / {@code workspace/didCreateFiles}</li>
 *     <li>{@code workspace/willDeleteFiles} / {@code workspace/didDeleteFiles}</li>
 *     <li>{@code workspace/willRenameFiles} / {@code workspace/didRenameFiles}</li>
 * </ul>
 * <p>
 * Internally, this class maintains a cache of {@link FileOperationMatcher}
 * instances, one per {@link FileOperationOptions}, to efficiently test
 * whether a given URI matches the file filters declared by the server.
 *
 * @see org.eclipse.lsp4j.FileOperationsServerCapabilities
 * @see org.eclipse.lsp4j.FileOperationOptions
 * @see FileOperationMatcher
 */
public class FileOperationsManager {

    private final LanguageServerWrapper languageServerWrapper;
    @Nullable
    private ServerCapabilities serverCapabilities;

    @NotNull
    private final Map<FileOperationOptions, FileOperationMatcher> matchers;

    /**
     * Creates a new {@code FileOperationsManager} bound to the given language server.
     *
     * @param languageServerWrapper the {@link LanguageServerWrapper} instance associated with this manager.
     */
    public FileOperationsManager(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
        this.matchers = new HashMap<>();
    }

    /**
     * Updates the current {@link ServerCapabilities} and clears any cached file matchers.
     *
     * @param serverCapabilities the capabilities to use, or {@code null} if not available.
     */
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        this.matchers.clear();
    }

    // -------------------------------------------------------------------------
    // Create files operations
    // -------------------------------------------------------------------------

    /**
     * Determines whether a {@code workspace/willCreateFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being created.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canWillCreateFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillCreate());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/willCreateFiles}, regardless of the target URI.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canWillCreateFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getWillCreate());
    }

    /**
     * Determines whether a {@code workspace/didCreateFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being created.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canDidCreateFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidCreate());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didCreateFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidCreateFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidCreate());
    }

    // -------------------------------------------------------------------------
    // Delete files operations
    // -------------------------------------------------------------------------

    /**
     * Determines whether a {@code workspace/willDeleteFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being deleted.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canWillDeleteFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillDelete());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/willDeleteFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canWillDeleteFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getWillDelete());
    }

    /**
     * Determines whether a {@code workspace/didDeleteFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being deleted.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canDidDeleteFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidDelete());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didDeleteFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidDeleteFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidDelete());
    }

    // -------------------------------------------------------------------------
    // Rename files operations
    // -------------------------------------------------------------------------

    /**
     * Determines whether a {@code workspace/willRenameFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being renamed.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canWillRenameFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillRename());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/willRenameFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canWillRenameFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getWillRename());
    }

    /**
     * Determines whether a {@code workspace/didRenameFiles} notification
     * can be sent for the given file or folder URI.
     *
     * @param fileUri  the URI of the file or folder being renamed.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if the operation is supported for this URI, {@code false} otherwise.
     */
    public boolean canDidRenameFiles(@NotNull URI fileUri, boolean isFolder) {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidRename());
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didRenameFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidRenameFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidRename());
    }

    // -------------------------------------------------------------------------
    // Internal utilities
    // -------------------------------------------------------------------------

    /**
     * Checks whether a specific file operation is supported for the given URI and options.
     *
     * @param fileUri              the URI of the file or folder.
     * @param isFolder             {@code true} if the target is a folder, {@code false} otherwise.
     * @param fileOperationOptions the file operation configuration from the server.
     * @return {@code true} if the file matches any filter defined in the operation, {@code false} otherwise.
     */
    private boolean canSupportFileOperation(@NotNull URI fileUri,
                                            boolean isFolder,
                                            @Nullable FileOperationOptions fileOperationOptions) {
        if (!hasFilters(fileOperationOptions)) {
            return false;
        }
        return getMatcher(fileOperationOptions).isMatch(fileUri, isFolder);
    }

    /**
     * Returns {@code true} if the given {@link FileOperationOptions} defines
     * at least one file filter.
     *
     * @param fileOperationOptions the file operation options.
     * @return {@code true} if one or more filters exist, {@code false} otherwise.
     */
    private static boolean hasFilters(@Nullable FileOperationOptions fileOperationOptions) {
        return fileOperationOptions != null
                && fileOperationOptions.getFilters() != null
                && !fileOperationOptions.getFilters().isEmpty();
    }

    /**
     * Retrieves or creates a cached {@link FileOperationMatcher} for the given
     * {@link FileOperationOptions}.
     *
     * @param fileOperationOptions the file operation configuration.
     * @return a matcher used to test URIs against the operation filters.
     */
    @NotNull
    private FileOperationMatcher getMatcher(FileOperationOptions fileOperationOptions) {
        var matcher = matchers.get(fileOperationOptions);
        if (matcher != null) {
            return matcher;
        }
        synchronized (matchers) {
            matchers.putIfAbsent(fileOperationOptions, new FileOperationMatcher(fileOperationOptions.getFilters()));
        }
        return matchers.get(fileOperationOptions);
    }

    /**
     * Returns the {@link FileOperationsServerCapabilities} declared by the server, if any.
     *
     * @return the file operations capabilities, or {@code null} if unavailable.
     */
    @Nullable
    private FileOperationsServerCapabilities getFileOperationsServerCapabilities() {
        if (serverCapabilities != null && serverCapabilities.getWorkspace() != null) {
            return serverCapabilities.getWorkspace().getFileOperations();
        }
        return null;
    }

    /**
     * Returns the {@link LanguageServerWrapper} associated with this manager.
     *
     * @return the language server wrapper.
     */
    public LanguageServerWrapper getLanguageServerWrapper() {
        return languageServerWrapper;
    }
}
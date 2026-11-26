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

import com.redhat.devtools.lsp4ij.LSPNotificationConstants;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * <p>
 * The filters of the file operations that the server registers dynamically
 * (see {@link #registerFileOperation(String, FileOperationOptions)}) apply
 * next to those of its capabilities.
 *
 * @see org.eclipse.lsp4j.FileOperationsServerCapabilities
 * @see org.eclipse.lsp4j.FileOperationOptions
 * @see FileOperationMatcher
 */
public class FileOperationsManager {

    // The methods of the file operations, which a language server can register dynamically
    private static final Set<String> FILE_OPERATION_METHODS = Set.of(
            LSPRequestConstants.WORKSPACE_WILL_CREATE_FILES,
            LSPNotificationConstants.WORKSPACE_DID_CREATE_FILES,
            LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES,
            LSPNotificationConstants.WORKSPACE_DID_RENAME_FILES,
            LSPRequestConstants.WORKSPACE_WILL_DELETE_FILES,
            LSPNotificationConstants.WORKSPACE_DID_DELETE_FILES);

    private final LanguageServerWrapper languageServerWrapper;
    @Nullable
    private ServerCapabilities serverCapabilities;

    @NotNull
    private final Map<FileOperationOptions, FileOperationMatcher> matchers;

    // The matchers of the filters that the language server registered dynamically, by the method of the file
    // operation, for the file operations that LSP4IJ announces in its client capabilities. Copy-on-write lists, which
    // are read while a registration changes them, as in the registries of the text document features
    @NotNull
    private final Map<String, List<FileOperationMatcher>> dynamicMatchers = Map.of(
            LSPNotificationConstants.WORKSPACE_DID_CREATE_FILES, new CopyOnWriteArrayList<>(),
            LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES, new CopyOnWriteArrayList<>(),
            LSPNotificationConstants.WORKSPACE_DID_RENAME_FILES, new CopyOnWriteArrayList<>(),
            LSPNotificationConstants.WORKSPACE_DID_DELETE_FILES, new CopyOnWriteArrayList<>());

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
     * Updates the current {@link ServerCapabilities} and clears any cached file matchers, and the filters of the
     * dynamic registrations.
     *
     * @param serverCapabilities the capabilities to use, or {@code null} if not available.
     */
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
        this.matchers.clear();
        this.dynamicMatchers.values().forEach(List::clear);
    }

    /**
     * Returns {@code true} if the given method is the method of a file operation, such as
     * {@code workspace/didRenameFiles}, which a language server can register dynamically.
     *
     * @param method the method of a registration.
     * @return {@code true} if the method is the method of a file operation, {@code false} otherwise.
     */
    public static boolean isFileOperation(@Nullable String method) {
        return method != null && FILE_OPERATION_METHODS.contains(method);
    }

    /**
     * Adds the filters of a dynamic registration of a file operation next to the filters of the server capabilities.
     *
     * @param method  the method of the file operation, such as {@code workspace/didRenameFiles}.
     * @param options the registration options, which hold the filters.
     * @return the action that removes the filters again, when the language server unregisters the file operation, or
     * {@code null} if LSP4IJ doesn't announce the file operation, as {@code workspace/willCreateFiles}, and ignores
     * its registration.
     */
    public @Nullable Runnable registerFileOperation(@NotNull String method, @NotNull FileOperationOptions options) {
        var registered = dynamicMatchers.get(method);
        if (registered == null) {
            return null;
        }
        var matcher = new FileOperationMatcher(options.getFilters() != null ? options.getFilters() : List.of());
        registered.add(matcher);
        return () -> registered.remove(matcher);
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
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidCreate())
                || isMatchedDynamically(LSPNotificationConstants.WORKSPACE_DID_CREATE_FILES, fileUri, isFolder);
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didCreateFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidCreateFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidCreate())
                || isRegisteredDynamically(LSPNotificationConstants.WORKSPACE_DID_CREATE_FILES);
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
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidDelete())
                || isMatchedDynamically(LSPNotificationConstants.WORKSPACE_DID_DELETE_FILES, fileUri, isFolder);
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didDeleteFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidDeleteFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidDelete())
                || isRegisteredDynamically(LSPNotificationConstants.WORKSPACE_DID_DELETE_FILES);
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
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getWillRename())
                || isMatchedDynamically(LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES, fileUri, isFolder);
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/willRenameFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canWillRenameFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getWillRename())
                || isRegisteredDynamically(LSPRequestConstants.WORKSPACE_WILL_RENAME_FILES);
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
        return capabilities != null && canSupportFileOperation(fileUri, isFolder, capabilities.getDidRename())
                || isMatchedDynamically(LSPNotificationConstants.WORKSPACE_DID_RENAME_FILES, fileUri, isFolder);
    }

    /**
     * Returns {@code true} if the language server declares support for
     * {@code workspace/didRenameFiles}.
     *
     * @return {@code true} if at least one filter is defined, {@code false} otherwise.
     */
    public boolean canDidRenameFiles() {
        var capabilities = getFileOperationsServerCapabilities();
        return capabilities != null && hasFilters(capabilities.getDidRename())
                || isRegisteredDynamically(LSPNotificationConstants.WORKSPACE_DID_RENAME_FILES);
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
     * Returns {@code true} if a dynamic registration of the given file operation has a filter that matches the given
     * file or folder URI.
     *
     * @param method   the method of the file operation.
     * @param fileUri  the URI of the file or folder.
     * @param isFolder {@code true} if the target is a folder, {@code false} otherwise.
     * @return {@code true} if a registered filter matches the URI, {@code false} otherwise.
     */
    private boolean isMatchedDynamically(@NotNull String method, @NotNull URI fileUri, boolean isFolder) {
        return dynamicMatchers.get(method).stream().anyMatch(matcher -> matcher.isMatch(fileUri, isFolder));
    }

    /**
     * Returns {@code true} if the language server registered the given file operation dynamically.
     *
     * @param method the method of the file operation.
     * @return {@code true} if a registration of the file operation is in effect, {@code false} otherwise.
     */
    private boolean isRegisteredDynamically(@NotNull String method) {
        return !dynamicMatchers.get(method).isEmpty();
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

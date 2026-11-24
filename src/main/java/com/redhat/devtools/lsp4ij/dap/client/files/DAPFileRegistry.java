/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registry of {@link DAPFile} instances for all projects and run/debug configurations.
 * <p>
 * This registry ensures that each disassembly file is unique per project and configuration, and each file of a source
 * given by source reference unique per project, configuration, source reference and name, and provides methods to
 * retrieve or create files on demand.
 * <p>
 * The virtual path of a disassembly file follows the format:
 * <pre>
 *     $projectLocationHash/$configName/disassembly
 * </pre>
 * and the one of the file of a source given by source reference:
 * <pre>
 *     $projectLocationHash/$configName/$sourceReference/$sourceName
 * </pre>
 * where {@code $projectLocationHash} is a unique identifier of the project location,
 * and {@code $configName} is the name of the run/debug configuration.
 */
public class DAPFileRegistry {

    /**
     * Cache mapping virtual paths to DisassemblyFile instances.
     */
    private static final Map<String, DAPFile> dapFilesCache = new HashMap<>();

    /**
     * Returns the singleton instance of this registry for the current application.
     *
     * @return the DisassemblyFileRegistry instance
     */
    public static @NotNull DAPFileRegistry getInstance() {
        return ApplicationManager.getApplication().getService(DAPFileRegistry.class);
    }

    /**
     * Retrieves an existing {@link DAPFile} for the given project, configuration and name,
     * or creates a new one if it does not exist: the disassembly file of the configuration for
     * {@link DisassemblyFile#FILE_NAME}, and otherwise the file of a source of that name, as the
     * {@code $projectLocationHash/$configName/$sourceName} paths of earlier versions, such as
     * the ones of persisted breakpoints, name it. A source given by source reference has a file
     * of its own: see {@link #getOrCreateDAPFile(String, int, String, Project)}.
     *
     * @param configName the name of the run/debug configuration
     * @param sourceName the name of the file
     * @param project    the project owning this file
     * @return the corresponding file
     */
    public @NotNull DAPFile getOrCreateDAPFile(@NotNull String configName,
                                               @NotNull String sourceName,
                                               @NotNull Project project) {
        // Construct the virtual path used as key in the cache
        String path = project.getLocationHash() + "/" + configName + "/" + sourceName;
        return getOrCreateFile(path, () -> DisassemblyFile.FILE_NAME.equals(sourceName) ?
                new DisassemblyFile(configName, path, project) : new SourceReferenceFile(sourceName, path, project));
    }

    /**
     * Retrieves the {@link DAPFile} which shows the text of a source given by source reference, or creates it if it
     * does not exist. Each source reference has its own file, named after the source, also when sources share a name,
     * such as the scripts evaluated with {@code eval}, which are all named {@code <anonymous>}. A source reference is
     * valid only in its debug session, while the file is kept for the run/debug configuration: a later session may
     * give another text for the same source reference, which replaces the one of its document.
     *
     * @param configName      the name of the run/debug configuration
     * @param sourceReference the source reference, greater than 0, which the debug adapter gives the text of
     * @param sourceName      the name of the source, which is the name of the file
     * @param project         the project owning the file
     * @return the file which shows the text of the source
     */
    public @NotNull DAPFile getOrCreateDAPFile(@NotNull String configName,
                                               int sourceReference,
                                               @NotNull String sourceName,
                                               @NotNull Project project) {
        String path = project.getLocationHash() + "/" + configName + "/" + sourceReference + "/" + sourceName;
        return getOrCreateFile(path, () -> new SourceReferenceFile(sourceName, path, project));
    }

    private @NotNull DAPFile getOrCreateFile(@NotNull String path, @NotNull Supplier<DAPFile> newFile) {
        // First attempt to get from cache without synchronization (fast path)
        var file = dapFilesCache.get(path);
        if (file != null) {
            return file;
        }

        // Synchronize on cache to avoid creating duplicates in multithreaded environment
        synchronized (dapFilesCache) {
            file = dapFilesCache.get(path);
            if (file != null) {
                return file;
            }

            // Create a new file and put it into the cache
            file = newFile.get();
            dapFilesCache.put(file.getPath(), file);
            return file;
        }
    }

    /**
     * Clears all cached {@link DAPFile} instances associated with the given project.
     * This should be called when a project is being closed to prevent memory leaks.
     *
     * @param project the project whose files should be cleared from the cache
     */
    public void clearProjectFiles(@NotNull Project project) {
        String projectPrefix = project.getLocationHash() + "/";
        synchronized (dapFilesCache) {
            dapFilesCache.entrySet().removeIf(entry -> entry.getKey().startsWith(projectPrefix));
        }
    }

}

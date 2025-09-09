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

/**
 * Registry of {@link DAPFile} instances for all projects and run/debug configurations.
 * <p>
 * This registry ensures that each disassembly file is unique per project and configuration
 * and provides methods to retrieve or create files on demand.
 * <p>
 * The virtual path of a disassembly file follows the format:
 * <pre>
 *     $projectLocationHash/$configName
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
     * Retrieves an existing {@link DAPFile} for the given project and configuration,
     * or creates a new one if it does not exist.
     *
     * @param configName the name of the run/debug configuration
     * @param configName the name of the run/debug configuration
     * @param project    the project owning this disassembly file
     * @return the corresponding DisassemblyFile instance
     */
    public @NotNull DAPFile getOrCreateDAPFile(@NotNull String configName,
                                               @NotNull String sourceName,
                                               @NotNull Project project) {
        // Construct the virtual path used as key in the cache
        String path = project.getLocationHash() + "/" + configName + "/" + sourceName;

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

            // Create a new DisassemblyFile and put it into the cache
            file = DisassemblyFile.FILE_NAME.equals(sourceName) ?  new DisassemblyFile(configName, path, project) : new SourceReferenceFile(sourceName, path, project);
            dapFilesCache.put(file.getPath(), file);
            return file;
        }
    }

}

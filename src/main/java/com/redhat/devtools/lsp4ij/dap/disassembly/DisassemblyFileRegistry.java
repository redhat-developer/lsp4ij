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
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of {@link DisassemblyFile} instances for all projects and run/debug configurations.
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
public class DisassemblyFileRegistry {

    /** Cache mapping virtual paths to DisassemblyFile instances. */
    private static final Map<String, DisassemblyFile> cache = new HashMap<>();

    /**
     * Returns the singleton instance of this registry for the current application.
     *
     * @return the DisassemblyFileRegistry instance
     */
    public static @NotNull DisassemblyFileRegistry getInstance() {
        return ApplicationManager.getApplication().getService(DisassemblyFileRegistry.class);
    }

    /**
     * Retrieves a {@link DisassemblyFile} from the cache by its virtual path.
     *
     * @param path the virtual path (format: "$projectLocationHash/$configName")
     * @return the DisassemblyFile if present, or null otherwise
     */
    public @Nullable DisassemblyFile getDisassemblyFile(String path) {
        return cache.get(path);
    }

    /**
     * Retrieves an existing {@link DisassemblyFile} for the given project and configuration,
     * or creates a new one if it does not exist.
     *
     * @param configName the name of the run/debug configuration
     * @param project    the project owning this disassembly file
     * @return the corresponding DisassemblyFile instance
     */
    public @NotNull DisassemblyFile getOrCreateDisassemblyFile(@NotNull String configName,
                                                               @NotNull Project project) {
        // Construct the virtual path used as key in the cache
        String path = project.getLocationHash() + "/" + configName;

        // First attempt to get from cache without synchronization (fast path)
        var file = cache.get(path);
        if (file != null) {
            return file;
        }

        // Synchronize on cache to avoid creating duplicates in multithreaded environment
        synchronized (cache) {
            file = cache.get(path);
            if (file != null) {
                return file;
            }

            // Create a new DisassemblyFile and put it into the cache
            file = new DisassemblyFile(configName, path, this, project);
            cache.put(file.getPath(), file);
            return file;
        }
    }

}

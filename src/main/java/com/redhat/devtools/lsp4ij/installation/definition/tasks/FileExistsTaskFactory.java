/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating {@link FileExistsTask} instances from JSON configuration.
 *
 * <pre>
 * "fileExists": {
 *   "name": "Check if server is installed",
 *   "file": "${serverDist}/js-debug/src/dapDebugServer.js"
 * }
 * </pre>
 */
public class FileExistsTaskFactory extends InstallerTaskFactoryBase {

    private static final String FILE_JSON_PROPERTY = "file";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return new FileExistsTask(id, name, onFail, onSuccess, getFile(json), serverInstallerDescriptor);
    }

    private static @NotNull String getFile(@NotNull JsonObject json) {
        String file = getStringFromOs(json, FILE_JSON_PROPERTY);
        return file != null ? file : "";
    }
}

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
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Factory for creating {@link InitializeTask} instances from JSON configuration.
 *
 * <pre>
 * "initialize": {
 *   "name": "Check that ruby-lsp starts",
 *   "timeout": 10000
 * }
 * </pre>
 */
public class InitializeTaskFactory extends InstallerTaskFactoryBase {

    private static final String TIMEOUT_JSON_PROPERTY = "timeout";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return new InitializeTask(id, name, onFail, onSuccess, getTimeout(json), serverInstallerDescriptor);
    }

    private static @Nullable Integer getTimeout(@NotNull JsonObject json) {
        return JSONUtils.getInteger(json, TIMEOUT_JSON_PROPERTY);
    }
}

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
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
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TODO: Revisit this task
 */
public class ShowMessageTaskFactory extends InstallerTaskFactoryBase {

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        @NotNull List<InstallerTask> actions = loadActions(json, serverInstallerDeclaration);
        return new ShowMessageTask(id, name, onFail, onSuccess, actions, serverInstallerDeclaration);
    }

    private static @NotNull List<InstallerTask> loadActions(@NotNull JsonObject json,
                                                            @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        var actions = JSONUtils.getJsonArray(json, "actions");
        if (actions == null) {
            return Collections.emptyList();
        }
        List<InstallerTask> stepsActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            var current = actions.get(i);
            if (current.isJsonObject()) {
                var jsonStep = current.getAsJsonObject();
                var action = serverInstallerDeclaration.getStepActionRegistry().loadTask(jsonStep, serverInstallerDeclaration);
                if (action != null) {
                    stepsActions.add(action);
                }
            }
        }
        return stepsActions;
    }
}

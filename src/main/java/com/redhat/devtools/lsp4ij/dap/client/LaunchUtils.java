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
package com.redhat.devtools.lsp4ij.dap.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Launch utilities.
 */
public class LaunchUtils {

    public enum VariableName {
        file,
        workspaceFolder
    }

    public static class LaunchContext extends HashMap<String, String> {

        public LaunchContext set(VariableName name, String value) {
            super.put("${" + name.name() + "}", value);
            return this;
        }
    }

    public static Map<String, Object> getDapParameters(DAPRunConfigurationOptions dapOptions) {
        LaunchContext context = new LaunchContext()
                .set(VariableName.file, getValidPath(dapOptions.getFile()))
                .set(VariableName.workspaceFolder, getValidPath(dapOptions.getWorkingDirectory()));
        return  getDapParameters(dapOptions.getDapParameters(), context);
    }

    @Nullable
    private static String getValidPath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        return path.replace("\\", "\\\\");
    }

    @NotNull
    public static Map<String, Object> getDapParameters(@Nullable String launchJson,
                                                       @Nullable Map<String, String> context) {
        if (StringUtils.isBlank(launchJson)) {
            return Collections.emptyMap();
        }

        for (Map.Entry<String, String> entry : context.entrySet()) {
            String value = entry.getValue();
            if (!StringUtils.isEmpty(value)) {
                launchJson = launchJson.replace(entry.getKey(), value);
            }
        }
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        // Conversion du JSON en Map
        return new Gson().fromJson(launchJson, mapType);
    }
}

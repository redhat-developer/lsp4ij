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

        public LaunchContext() {

        }

        public LaunchContext(@Nullable String file,
                             @Nullable String workspaceFolder) {
            set(VariableName.file, getValidPath(file))
                    .set(VariableName.workspaceFolder, getValidPath(workspaceFolder));
        }

        public LaunchContext set(@NotNull VariableName name,
                                 @Nullable String value) {
            if (value != null) {
                super.put("${" + name.name() + "}", value);
            }
            return this;
        }
    }

    public static Map<String, Object> getDapParameters(@NotNull DAPRunConfigurationOptions dapOptions) {
        LaunchContext context = new LaunchContext(dapOptions.getFile(), dapOptions.getWorkingDirectory());
        return getDapParameters(dapOptions.getDapParameters(), context);
    }

    @Nullable
    public static String getValidPath(@Nullable String path) {
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
        Type mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        // Conversion du JSON en Map
        return new Gson().fromJson(launchJson, mapType);
    }


    @NotNull
    public static String resolveAttachAddress(@Nullable  String attachAddress, @NotNull Map<String, Object> parameters) {
        if (StringUtils.isBlank(attachAddress)) {
            return "";
        }
        if (attachAddress.charAt(0) == '$') {
            var keys = attachAddress.substring(1).split("[.]");
            Object current = parameters;
            for (var key : keys) {
                if (current instanceof Map) {
                    current = ((Map) current).get(key);
                }
            }
            if (current != null) {
                return current.toString();
            }
            return "?";
        }
        return attachAddress;
    }

    public static int resolveAttachPort(@Nullable  String attachPort, Map<String, Object> parameters) {
        if (StringUtils.isBlank(attachPort)) {
            return -1;
        }
        if (attachPort.charAt(0) == '$') {
            var keys = attachPort.substring(1).split("[.]");
            Object current = parameters;
            for (var key : keys) {
                if (current instanceof Map) {
                    current = ((Map) current).get(key);
                }
            }
            if (current instanceof Double value) {
                return value.intValue();
            }
            if (current instanceof Float value) {
                return value.intValue();
            }
            if (current instanceof Long value) {
                return value.intValue();
            }
            if (current instanceof Integer value) {
                return value.intValue();
            }
            return -1;
        }
        try {
            return Integer.parseInt(attachPort);
        }
        catch(Exception e) {
            // Do nothing
        }
        return -1;
    }
}

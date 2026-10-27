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
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

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

    public static Map<String, Object> getDapParameters(
            @NotNull DAPRunConfigurationOptions dapOptions) {
        return getDapParameters(dapOptions, null);
    }

    public static Map<String, Object> getDapParameters(
            @NotNull DAPRunConfigurationOptions dapOptions,
            @Nullable Project project) {
        return getDapParameters(dapOptions, project, null);
    }

    public static Map<String, Object> getDapParameters(
            @NotNull DAPRunConfigurationOptions dapOptions,
            @Nullable Project project,
            @Nullable VirtualFile selectedFile) {

        LaunchContext context = new LaunchContext(
                dapOptions.getFile(), dapOptions.getWorkingDirectory());

        Map<String, Object> parameters =
                getDapParameters(dapOptions.getDapParameters(), context);

        if (project == null || parameters.isEmpty()) {
            return parameters;
        }

        DataContext dataContext = selectedFile == null
                ? null
                : SimpleDataContext.builder()
                        .add(CommonDataKeys.PROJECT, project)
                        .add(CommonDataKeys.VIRTUAL_FILE, selectedFile)
                        .build();

        Map<String, Object> expanded = new HashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            expanded.put(
                    entry.getKey(),
                    expandDapValue(entry.getValue(), project, dataContext));
        }
        return expanded;
    }

    private static Object expandDapValue(
            Object value,
            @NotNull Project project,
            @Nullable DataContext dataContext) {

        if (value instanceof String stringValue) {
            if (dataContext == null) {
                return resolveCommandLine(stringValue, project);
            }

            try {
                return MacroManager.getInstance()
                        .expandMacrosInString(stringValue, true, dataContext);
            } catch (Macro.ExecutionCancelledException e) {
                return stringValue;
            }
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> expanded = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    expanded.put(
                            key,
                            expandDapValue(entry.getValue(), project, dataContext));
                }
            }
            return expanded;
        }

        if (value instanceof List<?> list) {
            List<Object> expanded = new ArrayList<>();
            for (Object item : list) {
                expanded.add(expandDapValue(item, project, dataContext));
            }
            return expanded;
        }

        return value;
    }

    @Nullable
    public static String getValidPath(@Nullable String path) {
        if (path == null) {
            return null;
        }
        return path.replace("\\", "\\\\");
    }

    @NotNull
    public static Map<String, Object> getDapParameters(
            @Nullable String launchJson,
            @Nullable Map<String, String> context) {

        if (launchJson == null || StringUtils.isBlank(launchJson)) {
            return Collections.emptyMap();
        }

        if (context != null) {
            for (Map.Entry<String, String> entry : context.entrySet()) {
                String value = entry.getValue();
                if (!StringUtils.isEmpty(value)) {
                    launchJson = launchJson.replace(entry.getKey(), value);
                }
            }
        }

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(
                        new TypeToken<Map<String, Object>>() {}.getType(),
                        new IntegerPreservingMapDeserializer())
                .create();

        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        return gson.fromJson(launchJson, mapType);
    }

    @NotNull
    public static String resolveAttachAddress(
            @Nullable String attachAddress,
            @NotNull Map<String, Object> parameters) {

        if (attachAddress == null || StringUtils.isBlank(attachAddress)) {
            return "";
        }

        if (attachAddress.charAt(0) == '$') {
            var keys = attachAddress.substring(1).split("[.]");
            Object current = parameters;

            for (var key : keys) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(key);
                }
            }

            if (current != null) {
                return current.toString();
            }
            return "?";
        }

        return attachAddress;
    }

    public static int resolveAttachPort(
            @Nullable String attachPort,
            Map<String, Object> parameters) {

        if (attachPort == null || StringUtils.isBlank(attachPort)) {
            return -1;
        }

        if (attachPort.charAt(0) == '$') {
            var keys = attachPort.substring(1).split("[.]");
            Object current = parameters;

            for (var key : keys) {
                if (current instanceof Map) {
                    current = ((Map<?, ?>) current).get(key);
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
                return value;
            }
            return -1;
        }

        try {
            return Integer.parseInt(attachPort);
        } catch (Exception e) {
            // Do nothing.
        }

        return -1;
    }
}

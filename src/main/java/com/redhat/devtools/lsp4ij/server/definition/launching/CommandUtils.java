/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command utilities.
 */
public class CommandUtils {

    private static final Logger LOG = Logger.getInstance(CommandUtils.class);
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_EP = "com.intellij.commandLineEnvCustomizer";

    /** Caches reflective access to the optional IntelliJ command-line environment customizer API. */
    private static final class CommandLineEnvCustomizerReflection {

        private static final Method GET_ROOT_AREA;
        private static final Method GET_EXTENSION_POINT_IF_REGISTERED;
        private static final Method GET_EXTENSION_LIST;
        private static final Method CUSTOMIZE_ENV;

        static {
            Method getRootArea = null;
            Method getExtensionPointIfRegistered = null;
            Method getExtensionList = null;
            Method customizeEnv = null;

            try {
                Class<?> extensionsClass = Class.forName("com.intellij.openapi.extensions.Extensions");
                Class<?> extensionsAreaClass = Class.forName("com.intellij.openapi.extensions.ExtensionsArea");
                Class<?> extensionPointClass = Class.forName("com.intellij.openapi.extensions.ExtensionPoint");
                Class<?> customizerClass = Class.forName(
                        "com.intellij.execution.process.CommandLineEnvCustomizer");

                getRootArea = extensionsClass.getMethod("getRootArea");
                getExtensionPointIfRegistered = extensionsAreaClass.getMethod(
                        "getExtensionPointIfRegistered", String.class);
                getExtensionList = extensionPointClass.getMethod("getExtensionList");
                customizeEnv = customizerClass.getMethod(
                        "customizeEnv", GeneralCommandLine.class, Map.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Expected on IDE versions that do not provide CommandLineEnvCustomizer.
                LOG.debug("Directory-aware command-line environment customization is unavailable", e);
            }

            GET_ROOT_AREA = getRootArea;
            GET_EXTENSION_POINT_IF_REGISTERED = getExtensionPointIfRegistered;
            GET_EXTENSION_LIST = getExtensionList;
            CUSTOMIZE_ENV = customizeEnv;
        }

        private static boolean isAvailable() {
            return GET_ROOT_AREA != null
                    && GET_EXTENSION_POINT_IF_REGISTERED != null
                    && GET_EXTENSION_LIST != null
                    && CUSTOMIZE_ENV != null;
        }
    }

    /**
     * Returns the commands to execute with {@link Process} from the given commandline.
     *
     * @param commandLine the command line.
     *
     * @return the commands to execute with {@link Process} from the given commandline.
     */
    @NotNull
    public static List<String> createCommands(@NotNull String commandLine) {
        List<String> commands = new ArrayList<>();
        StringBuilder commandPart = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            switch(c) {
                case '"':
                    inString = !inString;
                    break;
                case ' ':
                    if (inString) {
                        commandPart.append(c);
                    } else {
                        addArg(commandPart, commands);
                        commandPart.setLength(0);
                    }
                    break;
                default:
                    commandPart.append(c);
                    break;
            }
        }
        if (commandPart.length() > 0) {
            addArg(commandPart, commands);
            commandPart.setLength(0);
        }
        return commands;
    }

    private static void addArg(StringBuilder commandPart, List<String> commands) {
        String arg = commandPart.toString().trim();
        if (!arg.isEmpty()) {
            commands.add(arg);
        }
    }

    @NotNull
    public static GeneralCommandLine createCommandLine(@NotNull String commandLine,
                                                       @NotNull Map<String, String> userEnvironmentVariables,
                                                       boolean includeSystemEnvironmentVariables) {
        return createCommandLine(commandLine, null, userEnvironmentVariables, includeSystemEnvironmentVariables);
    }

    @NotNull
    public static GeneralCommandLine createCommandLine(@NotNull String commandLine,
                                                       @Nullable String workingDir,
                                                       @NotNull Map<String, String> userEnvironmentVariables,
                                                       boolean includeSystemEnvironmentVariables) {
        Map<String, String> environmentVariables = new HashMap<>(userEnvironmentVariables);
        // Add System environment variables
        if (includeSystemEnvironmentVariables) {
            environmentVariables.putAll(EnvironmentUtil.getEnvironmentMap());
        }
        GeneralCommandLine generalCommandLine = new GeneralCommandLine(CommandUtils.createCommands(commandLine))
                .withEnvironment(environmentVariables);
        if (workingDir != null && !workingDir.isBlank()) {
            generalCommandLine.setWorkDirectory(workingDir);
            customizeEnvironmentForWorkingDirectory(generalCommandLine);
        }
        return generalCommandLine;
    }

    /**
     * Invokes the IntelliJ Platform's directory-aware command-line environment customizers when
     * they are available. Reflection metadata is cached by {@link CommandLineEnvCustomizerReflection}
     * to avoid repeated class and method lookups for every language server or debug adapter launch.
     *
     * <p>The extension point is optional on older IDEs. Its absence must not prevent a language
     * server or debug adapter from starting.</p>
     */
    private static void customizeEnvironmentForWorkingDirectory(@NotNull GeneralCommandLine commandLine) {
        if (!CommandLineEnvCustomizerReflection.isAvailable()) {
            return;
        }

        try {
            Object rootArea = CommandLineEnvCustomizerReflection.GET_ROOT_AREA.invoke(null);
            Object extensionPoint = CommandLineEnvCustomizerReflection.GET_EXTENSION_POINT_IF_REGISTERED
                    .invoke(rootArea, COMMAND_LINE_ENV_CUSTOMIZER_EP);
            if (extensionPoint == null) {
                return;
            }

            Object extensions = CommandLineEnvCustomizerReflection.GET_EXTENSION_LIST.invoke(extensionPoint);
            if (!(extensions instanceof Iterable<?> customizers)) {
                return;
            }

            Map<String, String> effectiveEnvironment = new HashMap<>(commandLine.getParentEnvironment());
            effectiveEnvironment.putAll(commandLine.getEnvironment());
            boolean customized = false;

            for (Object customizer : customizers) {
                try {
                    CommandLineEnvCustomizerReflection.CUSTOMIZE_ENV.invoke(
                            customizer, commandLine, effectiveEnvironment);
                    customized = true;
                } catch (InvocationTargetException e) {
                    LOG.warn("Failed to customize command-line environment for working directory "
                            + commandLine.getWorkDirectory(), e.getCause());
                }
            }

            if (customized) {
                commandLine.getEnvironment().clear();
                commandLine.getEnvironment().putAll(effectiveEnvironment);
                commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.debug("Directory-aware command-line environment customization is unavailable", e);
        }
    }

    /**
     * Returns the resolved command line with expanded macros.
     *
     * @param project the project.
     * @return the resolved command line with expanded macros.
     * @see <a href="https://www.jetbrains.com/help/idea/built-in-macros.html">Built In Macro</a>
     */
    public static String resolveCommandLine(@NotNull String commandLine, @Nullable Project project) {
        var nonNullProject = project!= null ? project : ProjectManager.getInstance().getDefaultProject();
        return ProgramParametersUtil.expandPathAndMacros(commandLine, null, nonNullProject);
    }

}

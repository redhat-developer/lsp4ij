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
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_CLASS =
            "com.intellij.execution.process.CommandLineEnvCustomizer";

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
     * they are available. This API was introduced after the platform version supported by LSP4IJ,
     * so it must be accessed reflectively to keep the plugin loadable on older IDEs.
     *
     * <p>The extension point is optional on older IDEs. Its absence must not prevent a language
     * server or debug adapter from starting.</p>
     */
    private static void customizeEnvironmentForWorkingDirectory(@NotNull GeneralCommandLine commandLine) {
        try {
            Class<?> extensionsClass = Class.forName("com.intellij.openapi.extensions.Extensions");
            Object rootArea = extensionsClass.getMethod("getRootArea").invoke(null);
            Class<?> extensionsAreaClass = Class.forName("com.intellij.openapi.extensions.ExtensionsArea");
            Method getExtensionPoint = extensionsAreaClass.getMethod("getExtensionPointIfRegistered", String.class);
            Object extensionPoint = getExtensionPoint.invoke(rootArea, COMMAND_LINE_ENV_CUSTOMIZER_EP);
            if (extensionPoint == null) {
                return;
            }

            Class<?> customizerClass = Class.forName(COMMAND_LINE_ENV_CUSTOMIZER_CLASS);
            Method customizeEnvironment = customizerClass.getMethod(
                    "customizeEnv", GeneralCommandLine.class, Map.class);
            Class<?> extensionPointClass = Class.forName("com.intellij.openapi.extensions.ExtensionPoint");
            Object extensions = extensionPointClass.getMethod("getExtensionList").invoke(extensionPoint);
            if (!(extensions instanceof Iterable<?> customizers)) {
                return;
            }

            Map<String, String> effectiveEnvironment = new HashMap<>(commandLine.getParentEnvironment());
            effectiveEnvironment.putAll(commandLine.getEnvironment());
            boolean customized = false;

            for (Object customizer : customizers) {
                try {
                    customizeEnvironment.invoke(customizer, commandLine, effectiveEnvironment);
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
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Expected on IDE versions that do not provide CommandLineEnvCustomizer.
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

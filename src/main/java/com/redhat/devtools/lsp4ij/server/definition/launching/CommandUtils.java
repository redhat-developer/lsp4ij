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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Command utilities.
 */
public class CommandUtils {

    private static final Logger LOG = Logger.getInstance(CommandUtils.class);
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_EP = "com.intellij.commandLineEnvCustomizer";
    private static final String COMMAND_LINE_ENV_CUSTOMIZER_CLASS =
            "com.intellij.execution.process.CommandLineEnvCustomizer";

    /**
     * Key used to track whether directory environment customization has already been executed
     * for a {@link GeneralCommandLine} instance.
     *
     * <p>This guard ensures idempotency within LSP4IJ's command-line preparation lifecycle,
     * preventing duplicate customization if the command line is passed through this helper multiple times.
     * It does not guarantee prevention of external or third-party customizer invocations outside LSP4IJ.</p>
     */
    static final Key<Boolean> ENV_CUSTOMIZED_KEY = Key.create("LSP4IJ_ENV_CUSTOMIZED");

    /** Caches reflective access to the optional IntelliJ command-line environment customizer API. */
    static final class CommandLineEnvCustomizerReflection {

        private static final Method CUSTOMIZE_ENV;
        private static volatile Method testCustomizeEnv;

        static {
            Method customizeEnv = null;
            try {
                Class<?> customizerClass = Class.forName(COMMAND_LINE_ENV_CUSTOMIZER_CLASS);
                customizeEnv = customizerClass.getMethod("customizeEnv", GeneralCommandLine.class, Map.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                // Expected on IDE versions that do not provide CommandLineEnvCustomizer (e.g. 2024.2).
                LOG.debug("Directory-aware command-line environment customization is unavailable", e);
            }
            CUSTOMIZE_ENV = customizeEnv;
        }

        static Method getCustomizeEnv() {
            Method testMethod = testCustomizeEnv;
            return testMethod != null ? testMethod : CUSTOMIZE_ENV;
        }

        static boolean isAvailable() {
            return getCustomizeEnv() != null;
        }

        @org.jetbrains.annotations.TestOnly
        static void setTestCustomizeEnv(@Nullable Method method) {
            testCustomizeEnv = method;
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
     *
     * <p>This method tracks execution on the {@link GeneralCommandLine} via {@link #ENV_CUSTOMIZED_KEY}
     * to ensure it runs only once per command-line instance within LSP4IJ.</p>
     *
     * @param commandLine the command line to customize.
     */
    public static void customizeEnvironmentForWorkingDirectory(@NotNull GeneralCommandLine commandLine) {
        if (!CommandLineEnvCustomizerReflection.isAvailable()) {
            return;
        }
        if (Boolean.TRUE.equals(commandLine.getUserData(ENV_CUSTOMIZED_KEY))) {
            return;
        }

        Application application = ApplicationManager.getApplication();
        if (application == null) {
            return;
        }

        ExtensionPoint<Object> ep;
        try {
            ep = application.getExtensionArea().getExtensionPointIfRegistered(COMMAND_LINE_ENV_CUSTOMIZER_EP);
        } catch (Exception e) {
            LOG.warn("Failed to retrieve command-line environment customizer extension point", e);
            return;
        }
        if (ep == null) {
            return;
        }

        Object[] customizers = ep.getExtensions();
        if (customizers.length == 0) {
            return;
        }

        applyCustomizers(commandLine, customizers);
    }

    /**
     * Applies the given customizers to the command line's environment.
     *
     * @param commandLine the command line to customize.
     * @param customizers the customizers to apply.
     */
    static void applyCustomizers(@NotNull GeneralCommandLine commandLine, @NotNull Object[] customizers) {
        if (customizers.length == 0 || !CommandLineEnvCustomizerReflection.isAvailable()) {
            return;
        }
        if (Boolean.TRUE.equals(commandLine.getUserData(ENV_CUSTOMIZED_KEY))) {
            return;
        }
        commandLine.putUserData(ENV_CUSTOMIZED_KEY, Boolean.TRUE);

        Map<String, String> effectiveEnvironment = mergeEnvironmentVariables(
                commandLine.getParentEnvironment(),
                commandLine.getEnvironment(),
                SystemInfo.isWindows);

        boolean anyCustomized = false;
        for (Object customizer : customizers) {
            Map<String, String> workingCopy = SystemInfo.isWindows
                    ? new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
                    : new HashMap<>();
            workingCopy.putAll(effectiveEnvironment);

            try {
                CommandLineEnvCustomizerReflection.getCustomizeEnv().invoke(
                        customizer, commandLine, workingCopy);
                effectiveEnvironment = workingCopy;
                anyCustomized = true;
            } catch (InvocationTargetException e) {
                LOG.warn("Failed to customize command-line environment for working directory "
                        + commandLine.getWorkDirectory(), e.getCause());
            } catch (Exception e) {
                LOG.warn("Unexpected error invoking command-line environment customizer "
                        + customizer.getClass().getName(), e);
            }
        }

        if (anyCustomized) {
            commandLine.getEnvironment().clear();
            commandLine.getEnvironment().putAll(effectiveEnvironment);
            commandLine.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE);
        }
    }

    /**
     * Merges inherited and configured environment variables.
     * Variable names are case-insensitive on Windows.
     *
     * @param parentEnvironment inherited environment variables.
     * @param configuredEnvironment explicitly configured environment variables.
     * @param caseInsensitive whether variable names should be compared case-insensitively.
     * @return the merged environment.
     */
    static Map<String, String> mergeEnvironmentVariables(
            Map<String, String> parentEnvironment,
            Map<String, String> configuredEnvironment,
            boolean caseInsensitive) {

        Map<String, String> effectiveEnvironment;
        if (caseInsensitive) {
            effectiveEnvironment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        } else {
            effectiveEnvironment = new HashMap<>();
        }

        if (parentEnvironment != null) {
            effectiveEnvironment.putAll(parentEnvironment);
        }
        if (configuredEnvironment != null) {
            effectiveEnvironment.putAll(configuredEnvironment);
        }
        return effectiveEnvironment;
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

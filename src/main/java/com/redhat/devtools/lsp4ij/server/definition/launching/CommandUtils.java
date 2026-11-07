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
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command utilities.
 */
public class CommandUtils {

    // The executable extensions of Windows when the environment has no PATHEXT
    private static final String WINDOWS_DEFAULT_PATHEXT = ".COM;.EXE;.BAT;.CMD";

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

    /**
     * Creates a {@link GeneralCommandLine} from the given command line and environment settings.
     *
     * @param commandLine the command line string to execute.
     * @param userEnvironmentVariables explicitly configured user environment variables.
     * @param includeSystemEnvironmentVariables whether to inherit system environment variables.
     * @return the initialized command line.
     */
    @NotNull
    public static GeneralCommandLine createCommandLine(@NotNull String commandLine,
                                                       @NotNull Map<String, String> userEnvironmentVariables,
                                                       boolean includeSystemEnvironmentVariables) {
        return createCommandLine(commandLine, null, userEnvironmentVariables, includeSystemEnvironmentVariables);
    }

    /**
     * Creates a {@link GeneralCommandLine} from the given command line, working directory and environment settings.
     * With a working directory, the executable is looked up in the environment that the process gets there, as
     * {@link #resolveExecutable(GeneralCommandLine)} does.
     *
     * @param commandLine the command line string to execute.
     * @param workingDir the working directory, or null if unspecified.
     * @param userEnvironmentVariables explicitly configured user environment variables.
     * @param includeSystemEnvironmentVariables whether to inherit system environment variables.
     * @return the initialized command line.
     */
    @NotNull
    public static GeneralCommandLine createCommandLine(@NotNull String commandLine,
                                                       @Nullable String workingDir,
                                                       @NotNull Map<String, String> userEnvironmentVariables,
                                                       boolean includeSystemEnvironmentVariables) {
        GeneralCommandLine generalCommandLine = new GeneralCommandLine(CommandUtils.createCommands(commandLine));
        if (includeSystemEnvironmentVariables) {
            generalCommandLine.withEnvironment(EnvironmentUtil.getEnvironmentMap());
        }
        // After the system variables, which they override. The command line's map ignores the case of a name on
        // Windows, as Windows does.
        generalCommandLine.withEnvironment(userEnvironmentVariables);
        if (workingDir != null && !workingDir.isBlank()) {
            generalCommandLine.setWorkDirectory(workingDir);
            resolveExecutable(generalCommandLine);
        }
        return generalCommandLine;
    }

    /**
     * Looks up the executable of a command line with a working directory in the PATH of the environment that its
     * process gets there, so that the tool which the directory provides, as with mise or direnv, is the one that
     * starts. A command line without a working directory, or whose executable is a path, is left as it is.
     * <p>
     * Since 2025.1, the platform lets plugins customize the environment of a process for its working directory
     * ({@code CommandLineEnvCustomizer}) when the process starts, but it looks up the executable before that, in the
     * PATH given to the command line only. {@link GeneralCommandLine#getEffectiveEnvironment()} is the environment
     * that the process gets, with those customizations. The environment itself is left to the platform, which
     * customizes it once, when the process starts.
     * <p>
     * On Windows, the executable is looked up as Windows does, see {@link #findInWindowsPath(String, String, String)}.
     *
     * @param commandLine the command line.
     */
    @ApiStatus.Internal
    public static void resolveExecutable(@NotNull GeneralCommandLine commandLine) {
        if (commandLine.getWorkDirectory() == null) {
            // No directory to take the environment from
            return;
        }
        String executable = commandLine.getExePath();
        if (executable.indexOf(File.separatorChar) != -1) {
            // A path to the executable, which the platform doesn't look up either
            return;
        }
        Map<String, String> environment = commandLine.getEffectiveEnvironment();
        String path = environment.get("PATH");
        if (path == null) {
            return;
        }
        File found = SystemInfo.isWindows
                ? findInWindowsPath(executable, path, environment.get("PATHEXT"))
                : PathEnvironmentVariableUtil.findInPath(executable, path, null);
        if (found != null) {
            commandLine.setExePath(found.getPath());
        }
    }

    /**
     * Looks up an executable in the given PATH as Windows does. A name without one of the executable extensions of
     * PATHEXT, such as {@code node} or {@code npx}, is looked up with each of them, directory by directory, so that
     * {@code node.exe} or {@code npx.cmd} is found, and not the shell script without an extension that Node.js ships
     * next to {@code npx.cmd}, which Windows can't start. A name with one of them is looked up as it is.
     * <p>
     * The platform's own lookup finds the exact name only, so a command such as {@code node} would otherwise be left to
     * Windows, which looks it up in the IDE's PATH, and not in the PATH of the working directory's environment.
     *
     * @param executable the name of the executable, without a path.
     * @param path       the PATH to look it up in.
     * @param pathExt    the PATHEXT of the environment, or null for the default executable extensions of Windows.
     * @return the executable, or null if none is found.
     */
    static @Nullable File findInWindowsPath(@NotNull String executable,
                                            @NotNull String path,
                                            @Nullable String pathExt) {
        List<String> extensions = StringUtil.split(pathExt != null ? pathExt : WINDOWS_DEFAULT_PATHEXT, ";", true, true)
                .stream()
                .filter(extension -> extension.startsWith("."))
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .toList();
        if (extensions.stream().anyMatch(extension -> StringUtil.endsWithIgnoreCase(executable, extension))) {
            return PathEnvironmentVariableUtil.findInPath(executable, path, null);
        }
        return PathEnvironmentVariableUtil.getPathDirs(path).stream()
                .map(File::new)
                .filter(File::isAbsolute)
                .flatMap(directory -> extensions.stream().map(extension -> new File(directory, executable + extension)))
                .filter(File::isFile)
                .findFirst()
                .orElse(null);
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

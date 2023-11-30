/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.intellij.lsp4ij.settings.UserDefinedLanguageServerSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A builder to create Java process command.
 */
public class JavaProcessCommandBuilder {

    private final String languageId;

    private String javaPath;

    private String debugPort;

    private boolean debugSuspend;
    private String jar;

    private String cp;

    public JavaProcessCommandBuilder(Project project, String languageId) {
        this.languageId = languageId;
        setJavaPath(computeJavaPath());
        UserDefinedLanguageServerSettings.LanguageServerDefinitionSettings settings = UserDefinedLanguageServerSettings.getInstance(project).getLanguageServerSettings(languageId);
        if (settings != null) {
            setDebugPort(settings.getDebugPort());
            setDebugSuspend(settings.isDebugSuspend());
        }
    }

    public JavaProcessCommandBuilder setJavaPath(String javaPath) {
        this.javaPath = javaPath;
        return this;
    }

    public JavaProcessCommandBuilder setDebugPort(String debugPort) {
        this.debugPort = debugPort;
        return this;
    }

    public JavaProcessCommandBuilder setDebugSuspend(boolean debugSuspend) {
        this.debugSuspend = debugSuspend;
        return this;
    }

    public JavaProcessCommandBuilder setJar(String jar) {
        this.jar = jar;
        return this;
    }

    public JavaProcessCommandBuilder setCp(String cp) {
        this.cp = cp;
        return this;
    }

    public List<String> create() {
        List<String> commands = new ArrayList<>();
        commands.add(javaPath);
        if (debugPort != null && !debugPort.isEmpty()) {
            String suspend = debugSuspend ? "y" : "n";
            commands.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspend + ",address=" + debugPort);
        }
        if (jar != null) {
            commands.add("-jar");
            commands.add(jar);
        }
        if (cp != null) {
            commands.add("-cp");
            commands.add(cp);
        }
        return commands;
    }

    private static String computeJavaPath() {
        return new File(System.getProperty("java.home"),
                "bin/java" + (OS.current() == OS.WINDOWS ? ".exe" : "")).getAbsolutePath();
    }
}

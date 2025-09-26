/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerManager;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationContext;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Base Action to install the selected language server definition/process.
 */
public abstract class InstallServerActionBase extends AnAction implements DumbAware {

    private final @NotNull LanguageServerDefinition serverDefinition;
    private final @NotNull Project project;
    private final @NotNull ServerInstallationContext context;

    public InstallServerActionBase(@NotNull ServerInstallationContext context,
                                   @NotNull LanguageServerDefinition serverDefinition,
                                   @NotNull Project project) {
        this.context = context
                .setStartServerAfterInstallation(false);
        this.serverDefinition = serverDefinition;
        this.project = project;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        LanguageServerManager.getInstance(project)
                .install(serverDefinition, context);
    }
}

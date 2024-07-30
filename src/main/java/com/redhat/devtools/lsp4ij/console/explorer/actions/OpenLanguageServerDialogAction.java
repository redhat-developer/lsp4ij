/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ui.NewLanguageServerDialog;
import org.jetbrains.annotations.NotNull;

/**
 * Action to open the new Language Server dialog.
 */
public class OpenLanguageServerDialogAction extends AnAction implements DumbAware {

        private final Project project;

        public OpenLanguageServerDialogAction(@NotNull Project project) {
            super(
                    LanguageServerBundle.message("action.lsp.console.new.language.server.text"),
                    LanguageServerBundle.message("action.lsp.console.new.language.server.description"),
                    AllIcons.Webreferences.Server);
            this.project = project;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            NewLanguageServerDialog dialog = new NewLanguageServerDialog(project);
            dialog.show();
        }

    }
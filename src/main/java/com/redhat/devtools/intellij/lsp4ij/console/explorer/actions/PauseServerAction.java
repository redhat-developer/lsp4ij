/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.console.explorer.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.treeStructure.Tree;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * Action to stop the selected language server process from the language explorer.
 */
public class PauseServerAction extends TreeAction implements DumbAware {

    public static final String ACTION_ID = "lsp.console.explorer.pause";
    @Override
    protected void actionPerformed(@NotNull Tree tree, @NotNull AnActionEvent e) {
        LanguageServerWrapper languageServer = getSelectedLanguageServer(tree);
        if (languageServer != null) {
            languageServer.stop();
        }
    }

}

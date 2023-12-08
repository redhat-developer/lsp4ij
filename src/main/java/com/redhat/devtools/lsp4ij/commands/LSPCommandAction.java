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
package com.redhat.devtools.lsp4ij.commands;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Abstract IJ {@link AnAction} class to execute an LSP {@link Command}.
 */
public abstract class LSPCommandAction extends AnAction {

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
        Command command = e.getData(CommandExecutor.LSP_COMMAND);
        if (command == null) {
            return;
        }
        commandPerformed(command, e);
    }

    /**
     * Returns the document URI which performs the action and null otherwise.
     *
     * @param e the action event.
     *
     * @return the document URI which performs the action and null otherwise.
     */
    protected @Nullable URI getDocumentUri(@NotNull AnActionEvent e) {
        return e.getData(CommandExecutor.LSP_COMMAND_DOCUMENT_URI);
    }

    /**
     * Performs the LSP command logic.
     *
     * @param command the LSP command.
     * @param e the action event.
     */
    protected abstract void commandPerformed(@NotNull Command command, @NotNull AnActionEvent e);
}

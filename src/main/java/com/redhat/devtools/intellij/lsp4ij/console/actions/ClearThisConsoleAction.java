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
package com.redhat.devtools.intellij.lsp4ij.console.actions;

import com.intellij.execution.actions.ClearConsoleAction;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Clear a given console view.
 *
 * @author Angelo ZERR
 */
public class ClearThisConsoleAction extends ClearConsoleAction {
    private final ConsoleView myConsoleView;

    public ClearThisConsoleAction(@NotNull ConsoleView consoleView) {
        myConsoleView = consoleView;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean enabled = myConsoleView.getContentSize() > 0;
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        myConsoleView.clear();
    }
}
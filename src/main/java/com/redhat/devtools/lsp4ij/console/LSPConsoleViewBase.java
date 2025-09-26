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
package com.redhat.devtools.lsp4ij.console;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.lsp4ij.console.actions.ClearThisConsoleAction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends {@link ConsoleViewImpl} to add base console actions.
 */
public class LSPConsoleViewBase extends ConsoleViewImpl {

    public LSPConsoleViewBase(@NotNull Project project,
                              @NotNull GlobalSearchScope searchScope,
                              boolean viewer,
                              boolean usePredefinedMessageFilter) {
        super(project, searchScope, viewer, usePredefinedMessageFilter);
    }

    @Override
    public AnAction @NotNull [] createConsoleActions() {
        // Don't call super.createConsoleActions() to avoid having some action like previous occurrence that we don't need.
        List<AnAction> consoleActions = new ArrayList<>();
        fillConsoleActions(consoleActions);
        return consoleActions.toArray(AnAction.EMPTY_ARRAY);
    }

    protected void fillConsoleActions(List<AnAction> consoleActions) {
        consoleActions.add(new ScrollToTheEndToolbarAction(getEditor()));
        consoleActions.add(ActionManager.getInstance().getAction("Print"));
        consoleActions.add(new ClearThisConsoleAction(this));
    }
}

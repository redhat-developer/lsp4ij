/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.filters.TextConsoleBuilderImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.console.LSPConsoleView;
import org.jetbrains.annotations.NotNull;

/**
 * Extends {@link TextConsoleBuilderImpl} to create the custom {@link LSPConsoleView}.
 */
public class DAPTextConsoleBuilderImpl extends TextConsoleBuilderImpl {

    public DAPTextConsoleBuilderImpl(@NotNull Project project) {
        super(project);
    }

    @Override
    protected @NotNull ConsoleView createConsole() {
        return new DAPConsoleView(getProject(), getScope(), isViewer(), isUsePredefinedMessageFilter());
    }
}

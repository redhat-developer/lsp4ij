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

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Extends {@link DuplexConsoleView} to support dual debug console (application console and debugger console).
 */
public class DAPDuplexConsoleView extends DuplexConsoleView<DAPConsoleView, DAPConsoleView> {

    public DAPDuplexConsoleView(@NotNull Project project,
                                @NotNull GlobalSearchScope searchScope,
                                boolean viewer,
                                boolean usePredefinedMessageFilter) {

        super(new DAPConsoleView(project, searchScope, viewer, usePredefinedMessageFilter), new DAPConsoleView(project, searchScope, viewer, usePredefinedMessageFilter));

        enableConsole(true);
        setDisableSwitchConsoleActionOnProcessEnd(false);

        getSwitchConsoleActionPresentation().setIcon(AllIcons.RunConfigurations.RemoteDebug);
        getSwitchConsoleActionPresentation().setText(DAPBundle.message("dap.console.log.show.dap"));
    }

    public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
        getSecondaryConsoleView().print(s, contentType);
    }

    public void attachToProcess(@NotNull ProcessHandler processHandler) {
        getSecondaryConsoleView().attachToProcess(processHandler);
    }
}

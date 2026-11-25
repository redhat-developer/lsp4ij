/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.xdebugger.frame.XSuspendContext;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ action "Step Back", which steps the current thread back with the 'stepBack' request, when the debug adapter
 * supports it (supportsStepBack).
 */
public class DAPStepBackAction extends DAPReverseExecutionAction {

    public static final @NotNull String ID = "DAP.StepBack";

    @Override
    protected void runBackwards(@NotNull DAPDebugProcess debugProcess, @Nullable XSuspendContext context) {
        debugProcess.startStepBack(context);
    }
}

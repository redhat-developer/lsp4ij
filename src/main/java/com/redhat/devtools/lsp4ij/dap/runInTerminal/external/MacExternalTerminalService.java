/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.runInTerminal.external;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * This class code is the vscode TypeScript translate from
 * <a href="https://github.com/microsoft/vscode/blob/62093721f21b6b178ea92d9f6c9da74c6afdeaf5/src/vs/platform/externalTerminal/node/externalTerminalService.ts#L144">MacExternalTerminalService</a>
 */
public class MacExternalTerminalService extends ExternalTerminalService {

    @Override
    public boolean isApplicable() {
        return SystemInfo.isMac;
    }

    @Override
    protected CompletableFuture<Void> doRunInTerminal(@NotNull RunInTerminalRequestArguments args, @NotNull Project project) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("MacExternalTerminalService not implemented"));
    }
}

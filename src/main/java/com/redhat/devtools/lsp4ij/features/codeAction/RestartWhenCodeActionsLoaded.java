/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Restarts the highlighting of a file once the code actions that the EDT found still loading have loaded.
 * <p>
 * The EDT never waits for a textDocument/codeAction answer: IntelliJ asks the lazy intentions for their text there,
 * when the lightbulb and the intention popup sort them, and a wait lasts as long as the language server takes. The
 * intentions are then not available yet, and the restarted intention pass, which reads the loaded code actions, shows
 * them.
 * <p>
 * It restarts once for each request, and only when the request completes normally. The restarted pass finds the
 * request done and sends no other, so a restart never leads to another; a request that fails or is cancelled restarts
 * nothing.
 */
public class RestartWhenCodeActionsLoaded {

    // The request whose completion restarts the highlighting; guarded by this
    private @Nullable CompletableFuture<?> scheduled;

    /**
     * Restarts the highlighting of the given file once the given request completes normally, unless a restart is
     * already scheduled for it.
     *
     * @param request the textDocument/codeAction request that the EDT did not wait for.
     * @param project the project.
     * @param file    the file whose code actions the request loads.
     */
    public void schedule(@NotNull CompletableFuture<?> request,
                         @NotNull Project project,
                         @NotNull VirtualFile file) {
        synchronized (this) {
            if (scheduled == request) {
                return;
            }
            scheduled = request;
        }
        request.thenRun(() -> ReadAction.nonBlocking((Callable<Void>) () -> {
                    PsiFile psiFile = file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
                    if (psiFile != null) {
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
                    }
                    return null;
                })
                .expireWith(project)
                .submit(AppExecutorUtil.getAppExecutorService()));
    }
}

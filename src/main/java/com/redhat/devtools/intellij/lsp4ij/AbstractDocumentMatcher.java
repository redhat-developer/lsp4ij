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
package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.internal.PromiseToCompletableFuture;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Abstract document matcher which prevent the execute of the match in an read action and when IJ is not indexing.
 */
public abstract class AbstractDocumentMatcher implements DocumentMatcher {

    private class CompletableFutureWrapper extends PromiseToCompletableFuture<Boolean> {

        public CompletableFutureWrapper(@NotNull VirtualFile file, @NotNull Project project) {
            super(indicator -> {
                        return AbstractDocumentMatcher.this.match(file, project);
                    }, "Match with " + AbstractDocumentMatcher.this.getClass().getName(),
                    project, null, AbstractDocumentMatcher.class, file.getUrl());
            init();
        }
    }

    @Override
    public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
        return new CompletableFutureWrapper(file, project);
    }

    @Override
    public boolean shouldBeMatchedAsynchronously(@NotNull Project project) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return false;
        }
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
            return true;
        }
        return DumbService.getInstance(project).isDumb();
    }
}

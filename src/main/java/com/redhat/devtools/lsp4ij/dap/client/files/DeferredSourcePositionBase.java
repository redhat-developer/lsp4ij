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
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileNavigator;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.impl.XSourcePositionEx;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Base class for an {@link com.intellij.xdebugger.XSourcePosition} whose final location is resolved asynchronously.
 * The debugger UI listens to {@link #getPositionUpdateFlow()} to refresh the execution point highlighting.
 */
abstract class DeferredSourcePositionBase implements XSourcePositionEx {

    private final MutableStateFlow<Boolean> resolvedFlow = StateFlowKt.MutableStateFlow(false);
    protected final @NotNull CompletableFuture<Void> resolveFuture = new CompletableFuture<>();

    protected final void markResolved() {
        resolvedFlow.setValue(true);
    }

    protected final boolean isResolved() {
        return resolvedFlow.getValue();
    }

    @Override
    public final @NotNull Flow<Boolean> getPositionUpdateFlow() {
        return resolvedFlow;
    }

    @Override
    public @NotNull Navigatable createNavigatable(@NotNull Project project) {
        return new DeferredNavigatable(project, this);
    }

    private static final class DeferredNavigatable extends OpenFileDescriptor {

        private final @NotNull DeferredSourcePositionBase position;

        private DeferredNavigatable(@NotNull Project project, @NotNull DeferredSourcePositionBase position) {
            super(project, position.getFile());
            this.position = position;
        }

        @Override
        public void navigate(boolean requestFocus) {
            if (position.isResolved()) {
                doNavigate(requestFocus);
            } else {
                position.resolveFuture.thenRun(() -> {
                    if (!position.isResolved()) {
                        return;
                    }
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (position.isResolved()) {
                            doNavigate(requestFocus);
                        }
                    });
                });
            }
        }

        private void doNavigate(boolean requestFocus) {
            VirtualFile file = position.getFile();
            if (!file.isValid()) {
                return;
            }
            FileNavigator.getInstance().navigate(createDescriptor(file), requestFocus);
        }

        @Override
        public void navigateIn(@NotNull Editor e) {
            if (position.isResolved()) {
                doNavigateIn(e);
            } else {
                position.resolveFuture.thenRun(() -> {
                    if (!position.isResolved()) {
                        return;
                    }
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (position.isResolved()) {
                            doNavigateIn(e);
                        }
                    });
                });
            }
        }

        private void doNavigateIn(@NotNull Editor editor) {
            VirtualFile file = position.getFile();
            if (!file.isValid()) {
                return;
            }
            createDescriptor(file).navigateIn(editor);
        }

        private @NotNull OpenFileDescriptor createDescriptor(@NotNull VirtualFile file) {
            int offset = position.getOffset();
            if (offset != -1) {
                return new OpenFileDescriptor(getProject(), file, offset);
            }
            return new OpenFileDescriptor(getProject(), file, Math.max(0, position.getLine()), 0);
        }

        @Override
        public boolean canNavigate() {
            return position.getFile().isValid();
        }

        @Override
        public boolean canNavigateToSource() {
            return canNavigate();
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * FalsePattern - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/*
 * Abstract class used to display IntelliJ inlay hints.
 */
public abstract class AbstractLSPDeclarativeInlayHintsProvider implements InlayHintsProvider {

    private static final SharedBypassCollector EMPTY_INLAY_HINTS_COLLECTOR = (psiElement, inlayHintsSink) -> {
        // Do nothing
    };

    @Override
    public @Nullable InlayHintsCollector createCollector(@NotNull PsiFile psiFile, @NotNull Editor editor) {
        if (ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            return EMPTY_INLAY_HINTS_COLLECTOR;
        }

        final long modificationStamp = psiFile.getModificationStamp();
        return new Collector(editor, modificationStamp);
    }

    protected void executeCommand(@Nullable Command command,
                                  @NotNull PsiFile file,
                                  @NotNull Editor editor,
                                  @Nullable InputEvent event,
                                  @NotNull LanguageServerItem languageServer) {
        if (command == null) {
            return;
        }
        LSPCommandContext context = new LSPCommandContext(command, file, LSPCommandContext.ExecutedBy.INLAY_HINT, editor, languageServer)
                .setSource(event != null ? (Component) event.getSource() : null)
                .setInputEvent(event);
        CommandExecutor.executeCommand(context);
    }

    protected abstract void doCollect(@NotNull PsiFile psiFile,
                                      @NotNull Editor editor,
                                      @NotNull InlayTreeSink inlayHintsSink,
                                      @NotNull List<CompletableFuture<?>> pendingFutures);


    private class Collector implements OwnBypassCollector {
        private final @NotNull Editor editor;
        private final long modificationStamp;

        public Collector(@NotNull Editor editor, long modificationStamp) {
            this.editor = editor;
            this.modificationStamp = modificationStamp;
        }

        @Override
        public void collectHintsForFile(@NotNull PsiFile psiFile, @NotNull InlayTreeSink inlayTreeSink) {
            Project project = psiFile.getProject();
            if (project.isDisposed()) {
                // InlayHint must not be collected
                return;
            }

            final List<CompletableFuture<?>> pendingFutures = new ArrayList<>();
            doCollect(psiFile, editor, inlayTreeSink, pendingFutures);
            if (!pendingFutures.isEmpty()) {
                // Some LSP requests:
                // - textDocument/inlayHint, inlayHint/resolve
                // are pending, wait for their completion and refresh the declarative inlay hints UI to render them
                EditorFeatureManager.getInstance(project).refreshEditorFeatureWhenAllDone(pendingFutures, modificationStamp, psiFile, EditorFeatureType.DECLARATIVE_INLAY_HINT);
            }
        }
    }
}

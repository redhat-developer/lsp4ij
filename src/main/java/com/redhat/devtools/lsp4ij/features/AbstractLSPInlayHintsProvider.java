/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.layout.LCFlags;
import com.intellij.ui.layout.LayoutKt;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import static com.redhat.devtools.lsp4ij.internal.InlayHintsFactoryBridge.refreshInlayHints;

/*
 * Abstract class used to display IntelliJ inlay hints.
 */
public abstract class AbstractLSPInlayHintsProvider implements InlayHintsProvider<NoSettings> {

    private static final InlayHintsCollector EMPTY_INLAY_HINTS_COLLECTOR = (psiElement, editor, inlayHintsSink) -> {
        // Do nothing
        return true;
    };

    private final SettingsKey<NoSettings> key = new SettingsKey<>("LSP.hints");


    @Nullable
    @Override
    public final InlayHintsCollector getCollectorFor(@NotNull PsiFile psiFile,
                                                     @NotNull Editor editor,
                                                     @NotNull NoSettings settings,
                                                     @NotNull InlayHintsSink inlayHintsSink) {

        if (!LanguageServersRegistry.getInstance().isFileSupported(psiFile)) {
            return EMPTY_INLAY_HINTS_COLLECTOR;
        }

        final long modificationStamp = psiFile.getModificationStamp();
        return new FactoryInlayHintsCollector(editor) {

            private boolean processed;

            @Override
            public boolean collect(@NotNull PsiElement psiElement, @NotNull Editor editor, @NotNull InlayHintsSink inlayHintsSink) {
                if (processed) {
                    // Before IJ 2023-3, FactoryInlayHintsCollector#collect(PsiElement element.. is called once time with PsiFile as element.
                    // Since IJ 2023-3, FactoryInlayHintsCollector#collect(PsiElement element.. is called several times for each token of the PsiFile
                    // which causes the problem of codelens/inlay hint which are not displayed because there are too many call of LSP request codelens/inlayHint which are cancelled.
                    // With IJ 2023-3 we need to collect LSP CodeLens/InlayHint just for the first call.
                    return false;
                }
                processed = true;
                Project project = psiFile.getProject();
                if (project.isDisposed()) {
                    // InlayHint must not be collected
                    return false;
                }
                try {
                    final List<CompletableFuture> pendingFutures = new ArrayList<>();
                    doCollect(psiFile, editor, getFactory(), inlayHintsSink, pendingFutures);
                    if (!pendingFutures.isEmpty()) {
                        // Some LSP requests:
                        // - textDocument/codeLens, codeLens/resolve
                        // - textDocument/inlayHint, inlayHint/resolve
                        // - textDocument/colorInformation
                        // are pending, wait for their completion and refresh the inlay hints UI to render them
                        CompletableFuture.allOf(pendingFutures.toArray(new CompletableFuture[0]))
                                .thenApplyAsync(_unused -> {
                                    // Check if PsiFile was not modified
                                    if (modificationStamp == psiFile.getModificationStamp()) {
                                        // All pending futures are finished, refresh the inlay hints
                                        refreshInlayHints(psiFile, new Editor[]{editor}, false);
                                    }
                                    return null;
                                });
                    }
                } catch (CancellationException e) {
                    // Do nothing
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return false;
            }
        };
    }


    @Override
    public boolean isVisibleInSettings() {
        return true;
    }

    @NotNull
    @Override
    public SettingsKey<NoSettings> getKey() {
        return key;
    }

    @NotNull
    @Override
    public String getName() {
        return "LSP";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return "Preview";
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull NoSettings o) {
        return new ImmediateConfigurable() {
            @NotNull
            @Override
            public JComponent createComponent(@NotNull ChangeListener changeListener) {
                return LayoutKt.panel(new LCFlags[0], "LSP", builder -> {
                    return null;
                });
            }
        };
    }

    @NotNull
    @Override
    public NoSettings createSettings() {
        return new NoSettings();
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language language) {
        return true;
    }

    protected void executeClientCommand(@Nullable Command command, @NotNull Editor editor, @Nullable InputEvent event) {
        if (command != null) {
            CommandExecutor.executeCommandClientSide(command, null, editor, editor.getProject(), event != null ? (Component) event.getSource() : null, event);
        }
    }

    protected abstract void doCollect(@NotNull PsiFile psiFile,
                                      @NotNull Editor editor,
                                      @NotNull PresentationFactory factory,
                                      @NotNull InlayHintsSink inlayHintsSink,
                                      @NotNull List<CompletableFuture> pendingFutures) throws InterruptedException;

}

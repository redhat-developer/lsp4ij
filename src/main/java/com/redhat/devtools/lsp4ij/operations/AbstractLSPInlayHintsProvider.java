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
package com.redhat.devtools.lsp4ij.operations;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.layout.LCFlags;
import com.intellij.ui.layout.LayoutKt;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CancellationException;

public abstract class AbstractLSPInlayHintsProvider implements InlayHintsProvider<NoSettings> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPInlayHintsProvider.class);

    private static final InlayHintsCollector EMPTY_INLAY_HINTS_COLLECTOR = new InlayHintsCollector() {

        @Override
        public boolean collect(@NotNull PsiElement psiElement, @NotNull Editor editor, @NotNull InlayHintsSink inlayHintsSink) {
            // Do nothing
            return true;
        }
    };

    private final Key<CancellationSupport> cancellationSupportKey;

    protected AbstractLSPInlayHintsProvider(Key<CancellationSupport> cancellationSupportKey) {
        this.cancellationSupportKey = cancellationSupportKey;
    }

    private SettingsKey<NoSettings> key = new SettingsKey<>("LSP.hints");


    @Nullable
    @Override
    public final InlayHintsCollector getCollectorFor(@NotNull PsiFile psiFile,
                                                     @NotNull Editor editor,
                                                     @NotNull NoSettings o,
                                                     @NotNull InlayHintsSink inlayHintsSink) {

        if (!LanguageServersRegistry.getInstance().isLanguageSupported(psiFile)) {
            return EMPTY_INLAY_HINTS_COLLECTOR;
        }

        CancellationSupport previousCancellationSupport = editor.getUserData(cancellationSupportKey);
        if (previousCancellationSupport != null) {
            previousCancellationSupport.cancel();
        }
        CancellationSupport cancellationSupport = new CancellationSupport();
        editor.putUserData(cancellationSupportKey, cancellationSupport);

        return new FactoryInlayHintsCollector(editor) {

            private boolean processed;

            @Override
            public boolean collect(@NotNull PsiElement psiElement, @NotNull Editor editor, @NotNull InlayHintsSink inlayHintsSink) {
                if (processed) {
                    // Before IJ 2023-3, FactoryInlayHintsCollector#collect(PsiElement element.. is called once time with PsiFile as element.
                    // Since IJ 2023-3, FactoryInlayHintsCollector#collect(PsiElement element.. is called several times for each token of the PsiFile
                    // which causes the problem of codelens/inlay hint which are not displayed because there are too many call of LSP request codelens/inlayhint which are cancelled.
                    // With IJ 2023-3 we need to collect LSP CodeLens/InlayHint just for the first call.
                    return false;
                }
                processed = true;
                VirtualFile file = getFile(psiFile);
                if (file == null) {
                    // InlayHint must not be collected
                    return false;
                }
                try {
                    doCollect(file, psiFile.getProject(), editor, getFactory(), inlayHintsSink, cancellationSupport);
                    cancellationSupport.checkCanceled();
                } catch (CancellationException e) {
                    // Do nothing
                } catch (ProcessCanceledException e) {
                    // Cancel all LSP requests
                    cancellationSupport.cancel();
                } catch (InterruptedException e) {
                    // Cancel all LSP requests
                    cancellationSupport.cancel();
                    LOGGER.warn(e.getLocalizedMessage(), e);
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

    protected void executeClientCommand(@NotNull Component source, @NotNull Command command, @NotNull Project project) {
        if (command != null) {
            CommandExecutor.executeCommandClientSide(command, null, project, source);
        }
    }

    protected abstract void doCollect(@NotNull VirtualFile file, @NotNull Project project, @NotNull Editor editor, @NotNull PresentationFactory factory, @NotNull InlayHintsSink inlayHintsSink, @NotNull CancellationSupport cancellationSupport) throws InterruptedException;

    /**
     * Returns the virtual file where inlay hint must be added and null otherwise.
     *
     * @param psiFile the psi file.
     * @return the virtual file where inlay hint must be added and null otherwise.
     */
    private @Nullable VirtualFile getFile(@NotNull PsiFile psiFile) {
        Project project = psiFile.getProject();
        if (project.isDisposed()) {
            // The project has been closed, don't collect inlay hints.
            return null;
        }
        return LSPIJUtils.getFile(psiFile);
    }
}

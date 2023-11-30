/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LSPVirtualFileData;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.lsp4ij.client.CoalesceByKey;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Utility class which receive LSP {@link PublishDiagnosticsParams}
 * from a language server and refresh the Annotation of the Intellij editor.
 *
 * @author Angelo ZERR
 */
public class LSPDiagnosticHandler implements Consumer<PublishDiagnosticsParams> {

    private final LanguageServerWrapper languageServerWrapper;

    public LSPDiagnosticHandler(LanguageServerWrapper languageServerWrapper) {
        this.languageServerWrapper = languageServerWrapper;
    }

    @Override
    public void accept(PublishDiagnosticsParams params) {
        Project project = languageServerWrapper.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            updateDiagnostics(params, project);
        } else {
            // Cancel if needed the previous "textDocument/publishDiagnostics" for a given uri.
            var coalesceBy = new CoalesceByKey("textDocument/publishDiagnostics", params.getUri());
            var executeInSmartMode = DumbService.getInstance(languageServerWrapper.getProject()).isDumb();
            var action = ReadAction.nonBlocking((Callable<Void>) () -> {
                            updateDiagnostics(params, project);
                            return null;
                        }).expireWith(languageServerWrapper)
                        .coalesceBy(coalesceBy);
            if (executeInSmartMode) {
                action.inSmartMode(project);
            }
            action.submit(AppExecutorUtil.getAppExecutorService());
        }
    }

    private void updateDiagnostics(@NotNull PublishDiagnosticsParams params, @NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        VirtualFile file = LSPIJUtils.findResourceFor(params.getUri());
        if (file == null) {
            return;
        }
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return;
        }

        // Update LSP diagnostic reported by the language server id
        URI fileURI = LSPIJUtils.toUri(file);
        LSPVirtualFileData data = languageServerWrapper.getLSPVirtualFileData(fileURI);
        if (data != null) {
            synchronized (data) {
                data.updateDiagnostics(params.getDiagnostics());
            }
            // Trigger Intellij validation to execute
            // {@link LSPDiagnosticAnnotator}.
            // which translates LSP Diagnostics into Intellij Annotation
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
    }
}
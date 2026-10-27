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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A highlighting pass that applies LSP diagnostics to the editor.
 *
 * <p>This pass is used ONLY for batch analysis ("Inspect Code").
 * In normal editor mode, diagnostics are applied by {@link LSPDiagnosticsApplier} (reactive).</p>
 */
public class LSPDiagnosticsPass extends TextEditorHighlightingPass implements DumbAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDiagnosticsPass.class);

    private final PsiFile psiFile;

    private List<HighlightInfo> highlightInfos = new ArrayList<>();

    public LSPDiagnosticsPass(@NotNull PsiFile file,
                              @NotNull Document document) {
        super(file.getProject(), document, false);
        this.psiFile = file;
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
        int groupId = LSPDiagnosticsApplier.GROUP_ID;
        if (groupId == -1) {
            return;
        }
        // Collect diagnostics
        LSPDiagnosticsCollector collector = new LSPDiagnosticsCollector(psiFile);
        List<HighlightInfo> highlights = collector.collect();
        highlightInfos = highlights != null ? highlights : new ArrayList<>();

        // Apply highlights
        BackgroundUpdateHighlightersUtil.setHighlightersToEditor(
                myProject,
                psiFile,
                myDocument,
                0,
                myDocument.getTextLength(),
                highlightInfos,
                groupId
        );
    }

    @Override
    public void doApplyInformationToEditor() {
        // Already applied in doCollectInformation
    }

    @NotNull
    @Override
    public List<HighlightInfo> getInfos() {
        return highlightInfos;
    }
}

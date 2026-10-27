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
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A highlighting pass that applies LSP document links to the editor.
 *
 * <p>Waits up to 500ms for document links. If not ready, registers a callback
 * with {@link LSPDocumentLinkApplier} to apply them when the future completes.</p>
 */
public class LSPDocumentLinkPass extends TextEditorHighlightingPass implements DumbAware {

    private final @NotNull PsiFile psiFile;
    private final @NotNull Document document;
    private final @Nullable Editor editor;

    private List<HighlightInfo> highlightInfos = new ArrayList<>();

    public LSPDocumentLinkPass(@NotNull PsiFile file,
                               @NotNull Document document,
                               @Nullable Editor editor) {
        super(file.getProject(), document, false);
        this.psiFile = file;
        this.document = document;
        this.editor = editor;
    }

    @Override
    public void doCollectInformation(@NotNull ProgressIndicator progress) {
        int groupId = LSPDocumentLinkApplier.GROUP_ID;
        if (groupId == -1) {
            return;
        }
        // Collect document links (waits up to 500ms)
        LSPDocumentLinkCollector collector = new LSPDocumentLinkCollector(psiFile, document);
        List<HighlightInfo> highlights = collector.collect();

        if (highlights != null) {
            // Document links ready - apply immediately
            highlightInfos = highlights;
            BackgroundUpdateHighlightersUtil.setHighlightersToEditor(
                    myProject,
                    psiFile,
                    document,
                    0,
                    document.getTextLength(),
                    highlightInfos,
                    groupId
            );
        } else {
            // Document links not ready yet - register callback to apply when ready
            highlightInfos = new ArrayList<>();
            if (editor != null) {
                collector.whenReady(() -> LSPDocumentLinkApplier.getInstance(myProject).scheduleRefresh(psiFile, document));
            }
        }
    }

    @Override
    public void doApplyInformationToEditor() {
        // Save PSI modification stamp
        if (editor != null) {
            editor.putUserData(LSPDocumentLinkPassFactory.PSI_MODIFICATION_STAMP,
                    LSPDocumentLinkPassFactory.getCurrentModificationStamp(psiFile));
        }
    }

    @NotNull
    @Override
    public List<HighlightInfo> getInfos() {
        return highlightInfos;
    }
}

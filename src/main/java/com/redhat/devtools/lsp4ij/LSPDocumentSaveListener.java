/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Sends {@code textDocument/willSave} and {@code textDocument/willSaveWaitUntil} before a document is saved, to the
 * language servers that have the document opened and ask for them.
 */
public class LSPDocumentSaveListener implements FileDocumentManagerListener {

    /**
     * How long a save waits, in all, for the edits of {@code textDocument/willSaveWaitUntil}: as long as VS Code waits
     * for its save participants.
     */
    private static final long WILL_SAVE_WAIT_UNTIL_TIMEOUT_MS = 1500L;

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        var file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return;
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WILL_SAVE_WAIT_UNTIL_TIMEOUT_MS);
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
            for (var wrapper : new ArrayList<>(LanguageServiceAccessor.getInstance(project).getStartedServers())) {
                if (!project.equals(wrapper.getProject())) {
                    continue;
                }
                var openedDocument = wrapper.getOpenedDocument(wrapper.toUri(file));
                if (openedDocument != null && openedDocument.getSynchronizer() != null) {
                    openedDocument.getSynchronizer().documentWillSave(deadlineNanos);
                }
            }
        }
    }
}

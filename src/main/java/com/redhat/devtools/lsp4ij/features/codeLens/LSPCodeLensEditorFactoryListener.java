/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeLens;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * Listens to editor events and manages the context for unresolved code lenses.
 * It updates the viewport lines and triggers the resolution and refresh of unresolved code lenses in the editor.
 */
public class LSPCodeLensEditorFactoryListener implements EditorFactoryListener {

    // Key used to store and retrieve the unresolved code lens context for each editor
    private static final Key<UnresolvedCodeLensViewportContext> CONTEXT_KEY = Key.create("unresolved.codelens.viewport.context");

    /**
     * Called when an editor is created.
     * Checks if the editor's file is supported and attaches a scroll listener.
     *
     * @param event The event associated with the editor creation.
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();

        if (project != null) {
            // If the file is supported, attach a scroll listener to the editor
            if (LanguageServersRegistry.getInstance().isFileSupported(editor.getVirtualFile(), project)) {
                attachScrollListener(editor);
            }
        }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        UnresolvedCodeLensViewportContext context = editor.getUserData(CONTEXT_KEY);
        if (context != null) {
            context.dispose();
            editor.putUserData(CONTEXT_KEY, null);
        }
    }

    /**
     * Attaches a listener to the editor's scrolling model to track the visible area changes.
     * When the visible area changes, it updates the viewport context and triggers the resolution of code lenses.
     *
     * @param editor The editor to which the scroll listener is attached.
     */
    private static void attachScrollListener(@NotNull Editor editor) {
        // Adding a listener for visible area changes
        editor.getScrollingModel().addVisibleAreaListener((e) -> {
            // Retrieve the code lens resolution context for the editor
            UnresolvedCodeLensViewportContext context = getCodeLensResolveContext(e.getEditor());
            context.updateViewportLines(e.getNewRectangle());

            // Retrieve the PsiFile and check if code lens data is ready
            PsiFile file = LSPIJUtils.getPsiFile(editor.getVirtualFile(), editor.getProject());
            LSPCodeLensSupport codeLensSupport = LSPFileSupport.getSupport(file).getCodeLensSupport();
            var data = codeLensSupport.getFuture();

            // If data is available and done, resolve and refresh code lenses in the viewport
            if (data != null && data.isDone()) {
                final long modificationStamp = file.getModificationStamp();
                final int firstViewportLine = context.getFirstViewportLine();
                final int lastViewportLine = context.getLastViewportLine();
                context.resolveAndRefreshUnresolvedCodeLensInViewport(data.getNow(Collections.emptyList()),
                        file,
                        firstViewportLine,
                        lastViewportLine,
                        modificationStamp);
            }
        });
    }

    /**
     * Retrieves the unresolved code lens resolution context for the given editor.
     * If no context exists, it synchronously creates and stores a new one.
     *
     * @param editor The editor for which the context is retrieved.
     * @return The unresolved code lens viewport context for the editor.
     */
    @NotNull
    public static UnresolvedCodeLensViewportContext getCodeLensResolveContext(@NotNull Editor editor) {
        UnresolvedCodeLensViewportContext context = editor.getUserData(CONTEXT_KEY);
        if (context != null) {
            return context;
        }
        return createCodeLensResolveContextSync(editor);
    }

    /**
     * Synchronously creates and stores a new unresolved code lens resolution context for the editor.
     *
     * @param editor The editor for which the context is created.
     * @return The newly created unresolved code lens viewport context.
     */
    @NotNull
    private synchronized static UnresolvedCodeLensViewportContext createCodeLensResolveContextSync(@NotNull Editor editor) {
        UnresolvedCodeLensViewportContext context = editor.getUserData(CONTEXT_KEY);
        if (context != null) {
            return context;
        }
        context = new UnresolvedCodeLensViewportContext(editor);
        editor.putUserData(CONTEXT_KEY, context);
        return context;
    }
}

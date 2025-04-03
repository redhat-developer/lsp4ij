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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listens to editor events and manages the context for unresolved code lenses.
 * It updates the viewport lines and triggers the resolution and refresh of unresolved code lenses in the editor.
 */
public class LSPCodeLensEditorFactoryListener implements EditorFactoryListener {

    // Key used to store and retrieve the unresolved code lens context for each editor
    private static final Key<UnresolvedCodeLensViewportContext> UNRESOLVED_CODELENS_CONTEXT_KEY = Key.create("unresolved.codelens.viewport.context");
    private static final Key<LSPCodeLensSupport> CODELENS_SUPPORT_KEY = Key.create("codelens.support.context");

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
            attachScrollListener(editor, project);
        }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        UnresolvedCodeLensViewportContext context = editor.getUserData(UNRESOLVED_CODELENS_CONTEXT_KEY);
        if (context != null) {
            context.dispose();
            editor.putUserData(UNRESOLVED_CODELENS_CONTEXT_KEY, null);
        }
        editor.putUserData(CODELENS_SUPPORT_KEY, null);
    }

    /**
     * Attaches a listener to the editor's scrolling model to track the visible area changes.
     * When the visible area changes, it updates the viewport context and triggers the resolution of code lenses.
     *
     * @param editor  The editor to which the scroll listener is attached.
     * @param project the project.
     */
    private static void attachScrollListener(@NotNull Editor editor,
                                             @NotNull Project project) {
        // Initialize context
        final var context = getCodeLensResolveContext(editor);
        // Adding a listener for visible area changes
        editor.getScrollingModel().addVisibleAreaListener((e) -> {
            if (e.getNewRectangle().equals(e.getOldRectangle())) {
                // View port range has no changed
                return;
            }
            // Update the first/last visible lines from the viewport
            context.updateViewportLines(e.getNewRectangle());

            LSPCodeLensSupport codeLensSupport = getCodeLensSupport(editor);
            if (codeLensSupport == null) {
                // The file is not linked to a language server which have LSP codeLens support.
                return;
            }

            if (context.hasFileChanged(codeLensSupport.getFile())) {
                // file has changed, do nothing
                return;
            }

            // Get valid LSP codelens future.
            var resultFuture = codeLensSupport.getFuture();
            if (resultFuture != null && resultFuture.isDone()) {
                var result = resultFuture.getNow(null);
                if (result == null || !result.hasToResolve(context.getFirstViewportLine(), context.getLastViewportLine())) {
                    // No codelens to resolve to the current viewport
                    return;
                }
                // Refresh the LSP code vision to resolve codelens visible in the viewport
                var file = codeLensSupport.getFile();
                final int firstViewportLine = context.getFirstViewportLine();
                final int lastViewportLine = context.getLastViewportLine();
                context.refreshCodeVision(result,
                        file,
                        firstViewportLine,
                        lastViewportLine);
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
        UnresolvedCodeLensViewportContext context = editor.getUserData(UNRESOLVED_CODELENS_CONTEXT_KEY);
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
        UnresolvedCodeLensViewportContext context = editor.getUserData(UNRESOLVED_CODELENS_CONTEXT_KEY);
        if (context != null) {
            return context;
        }
        context = new UnresolvedCodeLensViewportContext(editor);
        editor.putUserData(UNRESOLVED_CODELENS_CONTEXT_KEY, context);
        return context;
    }

    public static void setCodelensSupport(@NotNull Editor editor, LSPCodeLensSupport codeLensSupport) {
        editor.putUserData(CODELENS_SUPPORT_KEY, codeLensSupport);
    }

    @Nullable
    public static LSPCodeLensSupport getCodeLensSupport(@NotNull Editor editor) {
        return editor.getUserData(CODELENS_SUPPORT_KEY);
    }
}

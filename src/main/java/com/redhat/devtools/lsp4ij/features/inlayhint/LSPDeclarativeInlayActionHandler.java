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

import com.intellij.codeInsight.hints.declarative.InlayActionHandler;
import com.intellij.codeInsight.hints.declarative.InlayActionPayload;
import com.intellij.codeInsight.hints.declarative.PsiPointerInlayActionPayload;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * LSP textDocument/inlayHint command handling
 */
public class LSPDeclarativeInlayActionHandler implements InlayActionHandler {
    public static final String HANDLER_ID = "LSP4IJ";

    @Override
    public void handleClick(@NotNull Editor editor, @NotNull InlayActionPayload payload) {
        if (!(payload instanceof PsiPointerInlayActionPayload pointerPayload))
            return;
        var thePointer = pointerPayload.getPointer();
        if (!(thePointer instanceof LSPPayloadAction action))
            return;
        action.onClick(editor);
    }

    //This is technically a hack, but it seems to be reliable enough for the time being.
    public static InlayActionPayload createPayload(Project project, Consumer<Editor> callback) {
        return new PsiPointerInlayActionPayload(new LSPPayloadAction() {
            @Override
            public void onClick(Editor editor) {
                callback.accept(editor);
            }

            @Override
            public @NotNull Project getProject() {
                return project;
            }
        });
    }

    public interface LSPPayloadAction extends SmartPsiElementPointer<PsiElement> {
        void onClick(Editor editor);

        @Override
        default @Nullable PsiElement getElement() {
            return null;
        }

        @Override
        default @Nullable PsiFile getContainingFile() {
            return null;
        }

        @Override
        default VirtualFile getVirtualFile() {
            return null;
        }

        @Override
        default @Nullable Segment getRange() {
            return null;
        }

        @Override
        default @Nullable Segment getPsiRange() {
            return null;
        }
    }
}

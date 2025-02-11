/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TypedHandlerDelegate} implementation used to support LSP
 * completion triggerCharacters to open an IJ completion as popup
 * when language server defines the current typed character as completion trigger characters in the
 * server capability.
 */
public class LSPCompletionTriggerTypedHandler extends TypedHandlerDelegate {

    @NotNull
    @Override
    public Result checkAutoPopup(char charTyped, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (Character.isLetterOrDigit(charTyped) || charTyped == '_') {
            // Let's manage auto popup completion with CompletionAutoPopupHandler
            // See https://github.com/JetBrains/intellij-community/blob/def6433a5dd9f0a984cbc6e2835d27c97f2cb5f0/platform/lang-impl/src/com/intellij/codeInsight/editorActions/CompletionAutoPopupHandler.java#L44
            return Result.CONTINUE;
        }
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server.
            return Result.CONTINUE;
        }

        Result result = Result.CONTINUE;
        if (hasLanguageServerSupportingCompletionTriggerCharacters(charTyped, project, file)) {
            // The current typed character is defined as "completion trigger characters"
            // in a associated language server, open the completion.
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
            result = Result.STOP;
        }

        if (hasLanguageServerSupportingSignatureTriggerCharacters(charTyped, project, file)) {
            // The current typed character is defined as "signature trigger characters"
            // in a associated language server, open the signature help.
            AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
            result = Result.STOP;
        }

        return result;
    }

    @ApiStatus.Internal
    public static boolean hasLanguageServerSupportingCompletionTriggerCharacters(char charTyped, Project project, PsiFile file) {
        return LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures()
                        .getCompletionFeature()
                        .isCompletionTriggerCharactersSupported(file, String.valueOf(charTyped)));
    }

    private static boolean hasLanguageServerSupportingSignatureTriggerCharacters(char charTyped, Project project, PsiFile file) {
        return LanguageServiceAccessor.getInstance(project)
                .hasAny(file.getVirtualFile(), ls -> ls.getClientFeatures()
                                .getSignatureHelpFeature()
                                .isSignatureTriggerCharactersSupported(file, String.valueOf(charTyped)));
    }
}

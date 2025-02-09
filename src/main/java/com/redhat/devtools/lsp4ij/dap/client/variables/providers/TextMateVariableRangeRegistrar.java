/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

/**
 * This class is responsible for registering variable ranges in documents with the TextMate language syntax.
 * It implements the {@link VariableRangeRegistrar} interface and provides logic to detect variables based on
 * TextMate's element types and scopes.
 * <p>
 * The registrar will attempt to register a variable range if the token type corresponds to a variable,
 * as determined by its TextMate scope. This class assumes that any element in a TextMate language can be a variable
 * by default, though this logic can be customized.
 * </p>
 */
public class TextMateVariableRangeRegistrar implements VariableRangeRegistrar {

    @Override
    public boolean isApplicable(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
        return (file != null) && LSPIJEditorUtils.isSupportedTextMateFile(file);
    }

    @Override
    public boolean tryRegisterVariableRange(@NotNull IElementType tokenType,
                                            int start,
                                            int end,
                                            @NotNull Document document,
                                            @NotNull DebugVariableContext context) {
        // Retrieve the scope of the TextMate element if it's an instance of TextMateElementType.
        var textMateScope = tokenType instanceof TextMateElementType ? ((TextMateElementType) tokenType).getScope() : null;
        // If the scope is valid and represents a variable, register the range.
        if (textMateScope != null) {
            if (isVariable(textMateScope)) {
                var textRange = new TextRange(start, end);
                String variableName = document.getText(textRange);
                if (!variableName.isBlank()) {
                    context.addVariableRange(variableName.trim(), textRange);
                }
            }
            return true;
        }
        return false;
    }

    private boolean isVariable(TextMateScope textMateScope) {
        return textMateScope.getScopeName() == null /* in Julia TextMate, variable have null scope **/ ||
                ((String) textMateScope.getScopeName()).contains("source.") /* in Julia TextMate, variable have "source.julia"" scope **/ ||
                ((String) textMateScope.getScopeName()).contains("variable");
    }
}

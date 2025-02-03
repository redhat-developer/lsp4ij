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
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * This class implements the {@link VariableRangeRegistrar} interface and provides
 * the default behavior for registering variable ranges based on token types such as
 * "identifier" and "character". It is applicable for any file and registers ranges
 * for tokens that match those types.
 * <p>
 * The default registrar recognizes "identifier" and "character" token types as variables
 * and attempts to register their ranges in the provided debug context.
 * </p>
 */
public class DefaultVariableRangeRegistrar implements VariableRangeRegistrar {

    /**
     * Checks if this registrar is applicable for the given virtual file and project.
     * <p>
     * This default implementation returns true, meaning it will be applicable for
     * all file types and projects.
     * </p>
     *
     * @param virtualFile The file to check for applicability.
     * @param project The project in which the file is located.
     * @return true, indicating that this registrar is always applicable.
     */
    @Override
    public boolean isApplicable(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        return true;
    }

    /**
     * Attempts to register a variable range in the given document based on the token type.
     * <p>
     * This implementation checks if the token is of type "identifier" or "character",
     * and if so, registers its range in the provided debug variable context.
     * </p>
     *
     * @param tokenType The token type to check (e.g., identifier or character).
     * @param start The start offset of the token within the document.
     * @param end The end offset of the token within the document.
     * @param document The document containing the token.
     * @param context The context in which the variable range should be registered.
     * @return true if the variable range was successfully registered, false otherwise.
     */
    @Override
    public boolean tryRegisterVariableRange(@NotNull IElementType tokenType,
                                            int start,
                                            int end,
                                            @NotNull Document document,
                                            @NotNull DebugVariableContext context) {
        if ("identifier".equalsIgnoreCase(tokenType.getDebugName())
                || "character".equalsIgnoreCase(tokenType.getDebugName())) {
            var textRange = new TextRange(start, end);
            String variableName = document.getText(textRange);
            context.addVariableRange(variableName, textRange);
            return true;
        }
        return false;
    }
}

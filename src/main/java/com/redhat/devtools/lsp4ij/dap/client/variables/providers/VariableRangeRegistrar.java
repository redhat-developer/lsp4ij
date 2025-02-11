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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * This interface defines the contract for registering variable ranges in a document.
 * Implementations of this interface will be responsible for determining whether
 * they can register variable ranges based on the file type, and for performing
 * the registration itself within the given document.
 * <p>
 * The method {@link #tryRegisterVariableRange} will be called to attempt registering
 * variable ranges in a document based on the token type and other provided context.
 * </p>
 */
public interface VariableRangeRegistrar {

    /**
     * Checks if this registrar is applicable for the given virtual file and project.
     * <p>
     * This method will help filter the appropriate registrars based on the file type
     * and the project context. For example, it could be used to ensure that the registrar
     * is only applied to specific file types such as TextMate files.
     * </p>
     *
     * @param virtualFile The file to check for applicability.
     * @param project The project in which the file is located.
     * @return true if the registrar can be applied to the given file and project, false otherwise.
     */
    boolean isApplicable(@NotNull VirtualFile virtualFile,
                         @NotNull Project project);

    /**
     * Attempts to register a variable range in the given context based on the provided
     * token type, start, and end offsets within the document.
     * <p>
     * This method is responsible for checking if a token corresponds to a variable,
     * and if so, registering its range in the provided debug variable context.
     * </p>
     *
     * @param tokenType The token type to check.
     * @param start The start offset of the variable within the document.
     * @param end The end offset of the variable within the document.
     * @param document The document in which the token is located.
     * @param context The debug context in which the variable range should be registered.
     * @return true if the variable range was successfully registered, false otherwise.
     */
    boolean tryRegisterVariableRange(@NotNull IElementType tokenType,
                                     int start,
                                     int end,
                                     @NotNull Document document,
                                     @NotNull DebugVariableContext context);
}

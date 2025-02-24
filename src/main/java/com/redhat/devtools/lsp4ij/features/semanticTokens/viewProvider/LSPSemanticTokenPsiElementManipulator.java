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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * If not registered, an error is raised by the IDE about not being able to find an element manipulator. This is
 * fundamentally a no-op and just returns the original element.
 */
public class LSPSemanticTokenPsiElementManipulator extends AbstractElementManipulator<LSPSemanticTokenPsiElement> {
    @Override
    @Nullable
    public LSPSemanticTokenPsiElement handleContentChange(@NotNull LSPSemanticTokenPsiElement semanticTokenElement,
                                                          @NotNull TextRange textRange,
                                                          String newContent) throws IncorrectOperationException {
        // We can safely ignore the change and return the original element here
        return semanticTokenElement;
    }
}

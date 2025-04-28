/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentLink;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Documentation displayed when an LSP document link is triggered.
 */
public class LSPDocumentLinkDocumentationProvider implements DocumentationProvider {

    @Override
    public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        if (element instanceof LSPDocumentLinkPsiElement lspElement) {
            return lspElement.getLabel();
        }
        return null;
    }
}

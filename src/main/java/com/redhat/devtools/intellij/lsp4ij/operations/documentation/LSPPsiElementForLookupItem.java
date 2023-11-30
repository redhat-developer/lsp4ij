/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.operations.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import org.eclipse.lsp4j.MarkupContent;

import java.util.Collections;
import java.util.List;

/**
 * Implement a fake {@link PsiElement} which stores the completion item documentation.
 */
public class LSPPsiElementForLookupItem extends FakePsiElement {
    private final MarkupContent documentation;
    private final PsiManager psiManager;
    private final PsiElement element;

    public LSPPsiElementForLookupItem(MarkupContent documentation, PsiManager psiManager, PsiElement element) {
        this.psiManager = psiManager;
        this.documentation = documentation;
        this.element = element;
    }

    public List<MarkupContent> getDocumentation() {
        return documentation != null ? Collections.singletonList(documentation) : null;
    }

    public PsiElement getNavigationElement() {
        return element;
    }

    @Override
    public PsiElement getParent() {
        return getNavigationElement();
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public PsiFile getContainingFile() {
        return element.getContainingFile();
    }


    @Override
    public PsiManager getManager() {
        return psiManager;
    }

}

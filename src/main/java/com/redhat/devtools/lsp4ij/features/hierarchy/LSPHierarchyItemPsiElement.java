/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.features.hierarchy;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement a fake {@link PsiElement} which stores the required text edit (coming from Language server) to consume prepare call hierarchy.
 */
public class LSPHierarchyItemPsiElement extends LSPPsiElement {

    private static final TextRange DUMMY_TEXT_RANGE = new TextRange(0,0);
    private final @Nullable Range range;

    public LSPHierarchyItemPsiElement(@NotNull PsiFile file,
                                      @Nullable Range range,
                                      @NotNull String name) {
        super(file, DUMMY_TEXT_RANGE);
        super.setName(name);
        this.range = range;
    }

    public @Nullable Range getRange() {
        return range;
    }
}

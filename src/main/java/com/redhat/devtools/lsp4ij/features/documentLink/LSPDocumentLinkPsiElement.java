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

import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP document link used to create athel
 */
public class LSPDocumentLinkPsiElement extends FakePsiElement {

    private final String fileUri;
    private final @NotNull FileUriSupport fileUriSupport;
    private final @NotNull Project project;
    private final PsiFile file;

    public LSPDocumentLinkPsiElement(@NotNull String fileUri, @NotNull FileUriSupport fileUriSupport, @NotNull Project project){
        this.fileUri = fileUri;
        this.fileUriSupport = fileUriSupport;
        this.project = project;
        PsiFileFactory factory = PsiFileFactory.getInstance(project);
        file = factory.createFileFromText("dummy", UnknownFileType.INSTANCE, "");
    }

    @NotNull
    @Override
    public Project getProject() throws PsiInvalidElementAccessException {
        return file.getProject();
    }

    @Override
    public PsiElement getParent() {
        return file;
    }

    @Override
    public PsiFile getContainingFile() {
        return file;
    }


    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
        // The LSP document link file doesn't exist, open a file dialog
        // which asks if user want to create the file.
        // At this step we cannot open a dialog directly, we need to open the dialog
        // with invoke later.
        LSPIJUtils.openInEditor(fileUri, null, requestFocus, true, fileUriSupport, project);
    }

    @Override
    public @NlsSafe @Nullable String getLocationString() {
        return fileUri;
    }

    @Override
    public boolean isValid() {
        return true;
    }
}

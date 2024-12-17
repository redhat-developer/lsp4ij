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
package com.redhat.devtools.lsp4ij.features;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP Psi element factory.
 *
 * @param <T> the {@link LSPPsiElement} class to instance.
 */
public interface LSPPsiElementFactory<T extends LSPPsiElement> {

    public static final LSPPsiElementFactory<LSPPsiElement> DEFAULT = (psiFile, textRange) -> new LSPPsiElement(psiFile, textRange);

    /**
     * Create an instance of {@link LSPPsiElement}.
     *
     * @param psiFile   the Psi file.
     * @param textRange the text range.
     * @return an instance of {@link LSPPsiElement}.
     */
    T createPsiElement(@NotNull PsiFile psiFile, @NotNull TextRange textRange);

    /**
     * Create an instance of {@link LSPPsiElement} from the given LSP location and null otherwise.
     *
     * @param location the LSP location.
     * @param project  the project.
     * @return an instance of {@link LSPPsiElement} from the given LSP location and null otherwise.
     */
    @Nullable
    public static LSPPsiElement toPsiElement(@NotNull Location location,
                                             @Nullable FileUriSupport fileUriSupport,
                                             @NotNull Project project) {
        return toPsiElement(location, fileUriSupport, project, DEFAULT);
    }

    /**
     * Create an instance of {@link LSPPsiElement}  by using the given factory from the given LSP location and null otherwise.
     *
     * @param location the LSP location.
     * @param factory  the LSP Psi element factory.
     * @param project  the project.
     * @return an instance of {@link LSPPsiElement} from the given LSP location and null otherwise.
     */
    @Nullable
    public static <T extends LSPPsiElement> T toPsiElement(@NotNull Location location,
                                                           @Nullable FileUriSupport fileUriSupport,
                                                           @NotNull Project project,
                                                           @NotNull LSPPsiElementFactory<T> factory) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return doToPsiElement(location.getUri(), location.getRange(), fileUriSupport, project, factory);
        }
        return ReadAction.compute(() -> {
            return doToPsiElement(location.getUri(), location.getRange(), fileUriSupport, project, factory);
        });
    }

    /**
     * Create an instance of {@link LSPPsiElement} from the given LSP location link and null otherwise.
     *
     * @param location the LSP location.
     * @param project  the project.
     * @return an instance of {@link LSPPsiElement} from the given LSP location link and null otherwise.
     */
    public static LSPPsiElement toPsiElement(@NotNull LocationLink location,
                                             @Nullable FileUriSupport fileUriSupport,
                                             @NotNull Project project) {
        return toPsiElement(location, fileUriSupport, project, DEFAULT);
    }

    /**
     * Create an instance of {@link LSPPsiElement} by using the given factory from the given LSP location link and null otherwise.
     *
     * @param location the LSP location link.
     * @param factory  the LSP Psi element factory.
     * @param project  the project.
     * @return an instance of {@link LSPPsiElement} by using the given factory from the given LSP location link and null otherwise.
     */
    @Nullable
    public static <T extends LSPPsiElement> T toPsiElement(@NotNull LocationLink location,
                                                           @Nullable FileUriSupport fileUriSupport,
                                                           @NotNull Project project,
                                                           @NotNull LSPPsiElementFactory<T> factory) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return doToPsiElement(location.getTargetUri(), location.getTargetRange(), fileUriSupport, project, factory);
        }
        return ReadAction.compute(() -> {
            return doToPsiElement(location.getTargetUri(), location.getTargetRange(), fileUriSupport, project, factory);
        });
    }

    @Nullable
    private static <T extends LSPPsiElement> T doToPsiElement(@Nullable String uri,
                                                              @Nullable Range range,
                                                              @Nullable FileUriSupport fileUriSupport,
                                                              @NotNull Project project,
                                                              @NotNull LSPPsiElementFactory<T> factory) {
        if (uri == null || range == null) {
            return null;
        }
        VirtualFile file = FileUriSupport.findFileByUri(uri, fileUriSupport);
        if (file == null) {
            return null;
        }
        Document document = LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        TextRange textRange = LSPIJUtils.toTextRange(range, document, psiFile,true);
        if (textRange == null) {
            return null;
        }
        return factory.createPsiElement(psiFile, textRange);
    }
}

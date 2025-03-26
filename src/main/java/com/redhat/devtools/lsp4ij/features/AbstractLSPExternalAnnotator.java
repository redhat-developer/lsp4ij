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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for any LSP annotator.
 *
 * @param <InitialInfoType>
 * @param <AnnotationResultType>
 */
public abstract class AbstractLSPExternalAnnotator<InitialInfoType, AnnotationResultType> extends ExternalAnnotator<InitialInfoType, AnnotationResultType> {

    private final Key<Boolean> appliedKey;

    protected AbstractLSPExternalAnnotator(Key<Boolean> appliedKey) {
        this.appliedKey = appliedKey;
    }

    @Override
    public final void apply(@NotNull PsiFile file, @Nullable AnnotationResultType annotationResult, @NotNull AnnotationHolder holder) {
        if (isAlreadyApplied(file, holder.getCurrentAnnotationSession())) {
            return;
        }
        try {
            doApply(file, annotationResult, holder);
        }
        finally {
            holder.getCurrentAnnotationSession().putUserData(appliedKey, Boolean.TRUE);
        }
    }


    /**
     * Return true if the LSP external annotator has been already applied from the given session and false otherwise.
     *
     * @param file
     * @param session
     * @return true if the LSP external annotator has been already applied from the given session and false otherwise.
     */
    private boolean isAlreadyApplied(@NotNull PsiFile file, @NotNull AnnotationSession session) {
        if(session.getUserData(appliedKey) != null) {
            return true;
        }
        var files = file.getViewProvider().getAllFiles();
        if (files.size() > 1 && files.indexOf(file) > 0) {
            return true;
        }
        return false;
    }

    public abstract void doApply(@NotNull PsiFile file, @Nullable AnnotationResultType annotationResult, @NotNull AnnotationHolder holder);
}
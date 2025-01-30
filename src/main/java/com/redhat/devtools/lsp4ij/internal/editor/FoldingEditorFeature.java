/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Folding feature to refresh IntelliJ folding (even if Psi file doesn't change).
 *
 * Bridge class which consumes FoldingEditor with Java Reflection because FoldingEditorFeature#clearFoldingCache(editor) is private.
 */
@ApiStatus.Internal
public class FoldingEditorFeature implements EditorFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoldingEditorFeature.class);

    private static final String FOLDING_UPDATE_CLASS = "com.intellij.codeInsight.folding.impl.FoldingUpdate";

    // CodeVisionPassFactory.clearModificationStamp(editor)
    private Method clearFoldingCacheMethod;
    private Boolean loaded;

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.FOLDING;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        try {
            // Clear the modification stamp stored in the editor user-data
            // (with the key CODE_FOLDING_KEY)
            // to refresh the folding even if Psi file has not changed
            // see https://github.com/JetBrains/intellij-community/blob/7c8933354e46a99e1f41022aaa6552d2c0455eec/platform/lang-impl/src/com/intellij/codeInsight/folding/impl/FoldingUpdate.java#L66
            loadFoldingUpdateIfNeeded();
            if (clearFoldingCacheMethod != null) {
                clearFoldingCacheMethod.invoke(null, editor);
            }
        } catch (Exception e) {
            LOGGER.error("Error while calling FoldingUpdate.clearFoldingCache(editor)", e);
        }
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from folding range support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getFoldingRangeSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull List<Runnable> runnableList) {
        // Do nothing
    }

    private void loadFoldingUpdateIfNeeded() {
        if (loaded != null) {
            return;
        }
        loadFoldingUpdate();
    }

    private synchronized void loadFoldingUpdate() {
        if (loaded != null) {
            return;
        }
        try {
            Class<?> foldingUpdateClass = loadFoldingUpdateClass();
            // Get FoldingUpdate.clearFoldingCache(editor) method
            clearFoldingCacheMethod = foldingUpdateClass.getDeclaredMethod("clearFoldingCache", Editor.class);
            clearFoldingCacheMethod.setAccessible(true);
            this.loaded = Boolean.TRUE;
        }
        catch(Exception e) {
            this.loaded = Boolean.FALSE;
            LOGGER.error("Error while loading FoldingUpdate.clearFoldingCache(editor)", e);
        }
    }


    private static Class<?> loadFoldingUpdateClass() throws ClassNotFoundException {
            try {
                return Class.forName(FOLDING_UPDATE_CLASS);
            } catch (Exception e) {
                // Do nothing
            }
        LOGGER.error("Error while trying to load FoldingEditorFeature from classes " + FOLDING_UPDATE_CLASS);
        throw new ClassNotFoundException(FOLDING_UPDATE_CLASS);
    }

}

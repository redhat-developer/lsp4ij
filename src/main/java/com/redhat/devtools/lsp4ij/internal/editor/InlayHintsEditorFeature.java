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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Inlay hints feature to refresh IntelliJ inlay hints (even if Psi file doesn't change).
 *
 * Bridge class which consumes InlayHintsFactory with Java Reflection to support any IntelliJ version:
 *
 * <ul>
 *     <li>with old version (< 2024.1): com.intellij.codeInsight.hints.InlayHintsPassFactory</li>
 *     <li>with new version (>= 2024.1): com.intellij.codeInsight.hints.InlayHintsFactory</li>
 * </ul>
 */
@ApiStatus.Internal
public class InlayHintsEditorFeature implements EditorFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlayHintsEditorFeature.class);

    private static final String[] INLAY_HINTS_PASS_FACTORY_CLASSES = {
            "com.intellij.codeInsight.hints.InlayHintsFactory",
            "com.intellij.codeInsight.hints.InlayHintsPassFactory"};

    private static final Object INVALID_COMPANION_INSTANCE = new Object();

    // InlayHintsPassFactory.Companion
    private Object companionInstance;

    // InlayHintsPassFactory.Companion.clearModificationStamp(editor)
    private Method clearModificationStampMethod;

    private boolean errorLogged;

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.INLAY_HINT;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor, @NotNull Project project) {
        // old class: com.intellij.codeInsight.hints.InlayHintsPassFactory
        // --> InlayHintsPassFactory.Companion.clearModificationStamp(editor);
        //
        // new class: com.intellij.codeInsight.hints.InlayHintsFactory
        // --> InlayHintsFactory.Companion.clearModificationStamp(editor);
        try {
            loadInlayHintsPassFactoryIfNeeded();
            if (companionInstance != INVALID_COMPANION_INSTANCE) {
                clearModificationStampMethod.invoke(companionInstance, editor);
            }
        } catch (Exception e) {
            LOGGER.error("Error while calling InlayHintsPassFactory.Companion.clearModificationStamp(editor)", e);
            this.companionInstance = INVALID_COMPANION_INSTANCE;
        }
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from color support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getColorSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        Runnable runnable = () -> {
            // Refresh the annotations, inlay hints both
        	LSPFileSupport.getSupport(file).restartDaemonCodeAnalyzerWithDebounce();
        };
        runnableList.add(runnable);
    }

    private void loadInlayHintsPassFactoryIfNeeded() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        loadInlayHintsPassFactory();
    }

    private synchronized void loadInlayHintsPassFactory() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        Class<?> inlayHintsPassFactoryClass = loadInlayHintsPassFactoryClass();
        // Create instance of InlayHintsPassFactory
        Object inlayHintsPassFactoryInstance = inlayHintsPassFactoryClass.getConstructors()[0].newInstance();

        // Get InlayHintsPassFactory.Companion which defines relevant methods
        Class<?> companionClass = inlayHintsPassFactoryClass.getClasses()[0];

        // Get InlayHintsPassFactory.Companion.clearModificationStamp(editor) method
        this.clearModificationStampMethod = companionClass.getMethod("clearModificationStamp", Editor.class);
        this.clearModificationStampMethod.setAccessible(true);

        Field companionField = inlayHintsPassFactoryClass.getDeclaredField("Companion");
        companionField.setAccessible(true);
        this.companionInstance = companionField.get(inlayHintsPassFactoryInstance);
    }

    private static Class<?> loadInlayHintsPassFactoryClass() throws ClassNotFoundException {
        for (var className : INLAY_HINTS_PASS_FACTORY_CLASSES) {
            try {
                return Class.forName(className);
            } catch (Exception e) {
                // Do nothing
            }
        }
        String classNames = "[" + String.join(",", INLAY_HINTS_PASS_FACTORY_CLASSES) + "]";
        LOGGER.error("Error while trying to load InlayHintsPassFactory from classes " + classNames);
        throw new ClassNotFoundException(classNames);
    }

}

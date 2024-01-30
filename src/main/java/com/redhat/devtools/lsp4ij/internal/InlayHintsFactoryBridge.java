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
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bridge class which consumes InlayHintsFactory with Java Reflection to support any IntelliJ version:
 *
 * <ul>
 *     <li>with old version (< 2024.1): com.intellij.codeInsight.hints.InlayHintsPassFactory</li>
 *     <li>with new version (>= 2024.1): com.intellij.codeInsight.hints.InlayHintsFactory</li>
 * </ul>
 */
public class InlayHintsFactoryBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(InlayHintsFactoryBridge.class);

    private static final String[] INLAY_HINTS_PASS_FACTORY_CLASSES = {
            "com.intellij.codeInsight.hints.InlayHintsFactory",
            "com.intellij.codeInsight.hints.InlayHintsPassFactory"};

    // InlayHintsPassFactory.Companion
    private static Object companionInstance;

    // InlayHintsPassFactory.Companion.clearModificationStamp(editor)
    private static Method clearModificationStampMethod;

    /**
     * Refresh inlay hints for the given Psi file and editor.
     *
     * @param psiFile           the Psi file.
     * @param editors           the editors to refresh.
     * @param refreshLSPSupport true if codeLens, inlayHint, color support must be canceled and false otherwise.
     */
    public static void refreshInlayHints(@NotNull PsiFile psiFile, @NotNull Editor[] editors, boolean refreshLSPSupport) {
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            doRefreshHighlighting(psiFile, editors, refreshLSPSupport);
        } else {
            ReadAction.run(() -> doRefreshHighlighting(psiFile, editors, refreshLSPSupport));
        }
    }

    private static void doRefreshHighlighting(@NotNull PsiFile psiFile, @Nullable Editor[] editors, boolean refreshLSPSupport) {
        if (editors != null) {
            for (Editor editor : editors) {
                // Clear the modification stamp stored in the editor user-data
                // (with the key PSI_MODIFICATION_STAMP)
                // to refresh the inlay hints even if Psi file has not changed
                // see https://github.com/JetBrains/intellij-community/blob/9c675b406d27f908ea4abc2499e5d06310fc2fc6/platform/lang-impl/src/com/intellij/codeInsight/daemon/impl/InlayHintsPassFactoryInternal.kt#L42
                clearModificationStamp(editor);
            }
        }
        if (refreshLSPSupport) {
            // Evict the cache of LSP requests from inlayHint and color support
            var fileSupport = LSPFileSupport.getSupport(psiFile);
            fileSupport.getInlayHintsSupport().cancel();
            fileSupport.getColorSupport().cancel();
        }
        // Refresh the annotations, inlay hints both
        DaemonCodeAnalyzer.getInstance(psiFile.getProject()).restart(psiFile);
    }

    /**
     * Clear notification stamp from the given editor.
     *
     * @param editor the editor.
     */
    public static void clearModificationStamp(Editor editor) {
        // old class: com.intellij.codeInsight.hints.InlayHintsPassFactory
        // --> InlayHintsPassFactory.Companion.clearModificationStamp(editor);
        //
        // new class: com.intellij.codeInsight.hints.InlayHintsFactory
        // --> InlayHintsFactory.Companion.clearModificationStamp(editor);
        try {
            loadInlayHintsPassFactoryIfNeeded();
            clearModificationStampMethod.invoke(companionInstance, editor);
        } catch (Exception e) {
            LOGGER.error("Error while calling InlayHintsPassFactory.Companion.clearModificationStamp(editor)", e);
        }
    }

    private static void loadInlayHintsPassFactoryIfNeeded() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        loadInlayHintsPassFactory();
    }

    private static synchronized void loadInlayHintsPassFactory() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        Class<?> inlayHintsPassFactoryClass = loadInlayHintsPassFactoryClass();
        // Create instance of InlayHintsPassFactory
        Object inlayHintsPassFactoryInstance = inlayHintsPassFactoryClass.getConstructors()[0].newInstance();

        // Get InlayHintsPassFactory.Companion which defines relevant methods
        Class<?> companionClass = inlayHintsPassFactoryClass.getClasses()[0];
        Field companionField = inlayHintsPassFactoryClass.getDeclaredField("Companion");
        companionField.setAccessible(true);
        Object companionInstance = companionField.get(inlayHintsPassFactoryInstance);

        // Get InlayHintsPassFactory.Companion.clearModificationStamp(editor) method
        clearModificationStampMethod = companionClass.getMethod("clearModificationStamp", Editor.class);
        clearModificationStampMethod.setAccessible(true);
        InlayHintsFactoryBridge.companionInstance = companionInstance;
    }

    private static Class<?> loadInlayHintsPassFactoryClass() throws ClassNotFoundException {
        for (var className : INLAY_HINTS_PASS_FACTORY_CLASSES) {
            try {
                return Class.forName(className);
            } catch (Exception e) {
                // Do nothing
            }
        }
        String classNames = "[" + Stream.of(INLAY_HINTS_PASS_FACTORY_CLASSES)
                .collect(Collectors.joining(",")) + "]";
        LOGGER.error("Error while trying to load InlayHintsPassFactory from classes " + classNames);
        throw new ClassNotFoundException(classNames);
    }

}

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

import com.intellij.codeInsight.codeVision.CodeVisionHost;
import com.intellij.codeInsight.codeVision.CodeVisionInitializer;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.features.codeLens.DummyCodeVisionProviderFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Code vision feature to refresh IntelliJ code vision (even if Psi file doesn't change).
 * <p>
 * Bridge class which consumes CodeVisionPassFactory with Java Reflection to support any IntelliJ version:
 *
 * <ul>
 *     <li>with old version (< 2024.3): CodeVisionPassFactory.Companion.clearModificationStamp(Editor)</li>
 *     <li>with new version (>= 2024.3):  ModificationStampUtil.clearModificationStamp(Editor)</li>
 * </ul>
 */
@ApiStatus.Internal
public class CodeVisionEditorFeature implements EditorFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeVisionEditorFeature.class);

    public static final String MODIFICATION_STAMP_UTIL_CLASS_NAME = "com.intellij.codeInsight.hints.codeVision.ModificationStampUtil";

    private static final String[] CODE_VISION_PASS_FACTORY_CLASSES = {
            MODIFICATION_STAMP_UTIL_CLASS_NAME,
            "com.intellij.codeInsight.hints.codeVision.CodeVisionPassFactory"};

    private static final Object INVALID_COMPANION_INSTANCE = new Object();

    // CodeVisionPassFactory.Companion
    private Object companionInstance;

    // CodeVisionPassFactory.clearModificationStamp(editor)
    private Method clearModificationStampMethod;

    @Override
    public EditorFeatureType getFeatureType() {
        return EditorFeatureType.CODE_VISION;
    }

    @Override
    public void clearEditorCache(@NotNull Editor editor) {
        try {
            // Clear the modification stamp stored in the editor user-data
            // (with the key PSI_MODIFICATION_STAMP)
            // to refresh the code vision even if Psi file has not changed
            // see https://github.com/JetBrains/intellij-community/blob/7c8933354e46a99e1f41022aaa6552d2c0455eec/platform/lang-impl/src/com/intellij/codeInsight/hints/codeVision/CodeVisionPassFactory.kt#L32
            loadModificationStampUtilIfNeeded();
            if (companionInstance != INVALID_COMPANION_INSTANCE) {
                clearModificationStampMethod.invoke(companionInstance, editor);
            }
        } catch (Exception e) {
            // Log the error just one time
            LOGGER.error("Error while calling ModificationStampUtil.clearModificationStamp(Editor) or CodeVisionPassFactory.Companion.clearModificationStamp(Editor) ", e);
            companionInstance = INVALID_COMPANION_INSTANCE;
        }
    }

    @Override
    public void clearLSPCache(PsiFile file) {
        // Evict the cache of LSP requests from codeLens support
        var fileSupport = LSPFileSupport.getSupport(file);
        fileSupport.getCodeLensSupport().cancel();
    }

    @Override
    public void collectUiRunnable(@NotNull Editor editor,
                                  @NotNull PsiFile file,
                                  @NotNull List<Runnable> runnableList) {
        Runnable runnable = () -> {
            // Get the IntelliJ code vision host and fire an event to refresh only LSP code vision
            var codeVisionHost = CodeVisionInitializer.Companion.getInstance(file.getProject()).getCodeVisionHost();
            codeVisionHost.getInvalidateProviderSignal().fire(new CodeVisionHost.LensInvalidateSignal(editor, DummyCodeVisionProviderFactory.LSP_CODE_VISION_PROVIDER_IDS));
        };
        runnableList.add(runnable);
    }

    private void loadModificationStampUtilIfNeeded() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        loadModificationStampUtil();
    }

    private synchronized void loadModificationStampUtil() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        Class<?> modificationStampUtilClass = loadCodeVisionPassFactoryClass();
        if (modificationStampUtilClass.getName().equals(MODIFICATION_STAMP_UTIL_CLASS_NAME)) {
            // Ij >= 2024.3 -> consume ModificationStampUtil.clearModificationStamp(Editor)
            this.clearModificationStampMethod = modificationStampUtilClass.getMethod("clearModificationStamp", Editor.class);
            this.clearModificationStampMethod.setAccessible(true);

            var constructor = modificationStampUtilClass.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            this.companionInstance = constructor.newInstance();

            return;
        }
        // IJ < 2024.3 -> consume CodeVisionPassFactory.Companion.clearModificationStamp(Editor)
        // Create instance of CodeVisionPassFactory
        Object codeVisionPassFactoryInstance = modificationStampUtilClass.getConstructors()[0].newInstance();

        Class<?> companionClass = modificationStampUtilClass.getClasses()[0];

        // Get
        // - CodeVisionPassFactory.Companion.clearModificationStamp(editor) method -> Old version of IJ (< 2024.3)
        this.clearModificationStampMethod = companionClass.getMethod("clearModificationStamp", Editor.class);
        this.clearModificationStampMethod.setAccessible(true);

        Field companionField = getCompanionInstance(modificationStampUtilClass);
        companionField.setAccessible(true);
        this.companionInstance = companionField.get(codeVisionPassFactoryInstance);
    }

    private static Class<?> loadCodeVisionPassFactoryClass() throws ClassNotFoundException {
        for (var className : CODE_VISION_PASS_FACTORY_CLASSES) {
            try {
                return Class.forName(className);
            } catch (Exception e) {
                // Do nothing
            }
        }
        String classNames = "[" + String.join(",", CODE_VISION_PASS_FACTORY_CLASSES) + "]";
        LOGGER.error("Error while trying to initialize CodeVisionEditorFeature from classes " + classNames);
        throw new ClassNotFoundException(classNames);
    }

    private static Field getCompanionInstance(Class<?> codeVisionPassFactoryClass) throws NoSuchFieldException {
        try {
            return codeVisionPassFactoryClass.getDeclaredField("ModificationStampUtil");
        } catch (NoSuchFieldException e) {
            // Old version of IJ (< 2024.3)
            return codeVisionPassFactoryClass.getDeclaredField("Companion");
        }
    }

}

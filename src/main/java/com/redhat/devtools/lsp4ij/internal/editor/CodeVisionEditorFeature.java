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
 *
 * Bridge class which consumes CodeVisionPassFactory with Java Reflection to support any IntelliJ version:
 *
 * <ul>
 *     <li>with old version (< 2024.3): CodeVisionPassFactory.Companion.clearModificationStamp(editor)</li>
 *     <li>with new version (>= 2024.3):  CodeVisionPassFactory.ModificationStampUtil.clearModificationStamp(editor)</li>
 * </ul>
 */
@ApiStatus.Internal
public class CodeVisionEditorFeature implements EditorFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeVisionEditorFeature.class);

    private static final String CODE_VISION_PASS_FACTORY_CLASS = "com.intellij.codeInsight.hints.codeVision.CodeVisionPassFactory";

    // CodeVisionPassFactory.Companion
    private static Object companionInstance;

    // CodeVisionPassFactory.clearModificationStamp(editor)
    private static Method clearModificationStampMethod;

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
            loadCodeVisionPassFactoryIfNeeded();
            clearModificationStampMethod.invoke(companionInstance, editor);
        } catch (Exception e) {
            LOGGER.error("Error while calling CodeVisionPassFactory.ModificationStampUtil.clearModificationStamp(editor)", e);
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

    private static void loadCodeVisionPassFactoryIfNeeded() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        loadCodeVisionPassFactory();
    }

    private static synchronized void loadCodeVisionPassFactory() throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchFieldException, NoSuchMethodException {
        if (companionInstance != null) {
            return;
        }
        Class<?> codeVisionPassFactoryClass = loadCodeVisionPassFactoryClass();
        // Create instance of CodeVisionPassFactory
        Object codeVisionPassFactoryInstance = codeVisionPassFactoryClass.getConstructors()[0].newInstance();

        // Get CodeVisionPassFactory.ModificationStampUtil which defines relevant methods
        Class<?> companionClass = codeVisionPassFactoryClass.getClasses()[0];
        Field companionField = getCompanionInstance(codeVisionPassFactoryClass);
        companionField.setAccessible(true);
        Object companionInstance = companionField.get(codeVisionPassFactoryInstance);

        // Get
        // - CodeVisionPassFactory.ModificationStampUtil.clearModificationStamp(editor) method -> New version of IJ (>= 2024.3)
        // - CodeVisionPassFactory.Companion.clearModificationStamp(editor) method -> Old version of IJ (< 2024.3)
        clearModificationStampMethod = companionClass.getMethod("clearModificationStamp", Editor.class);
        clearModificationStampMethod.setAccessible(true);
        CodeVisionEditorFeature.companionInstance = companionInstance;
    }

    private static Class<?> loadCodeVisionPassFactoryClass() throws ClassNotFoundException {
        try {
            return Class.forName(CODE_VISION_PASS_FACTORY_CLASS);
        } catch (Exception e) {
            // Do nothing
        }
        LOGGER.error("Error while trying to initialize CodeVisionEditorFeature from classes " + CODE_VISION_PASS_FACTORY_CLASS);
        throw new ClassNotFoundException(CODE_VISION_PASS_FACTORY_CLASS);
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

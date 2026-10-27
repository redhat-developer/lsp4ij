/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Reflection-based wrapper for creating {@link AnnotationSession} and {@link AnnotationHolder}.
 *
 * <p>Uses reflection to access internal IntelliJ classes {@code AnnotationSessionImpl} and
 * {@code AnnotationHolderImpl} which are marked as internal but have no public alternative
 * for converting LSP diagnostics to HighlightInfo outside an Annotator context.</p>
 *
 * <p>This approach is more resilient to IntelliJ API changes than direct imports, as it will
 * gracefully fail if the classes are renamed or removed.</p>
 */
public class AnnotationHolderReflection {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationHolderReflection.class);

    private static final Class<?> ANNOTATION_SESSION_IMPL_CLASS;
    private static final Method CREATE_SESSION_METHOD;
    private static final Class<?> ANNOTATION_HOLDER_IMPL_CLASS;
    private static final Constructor<?> ANNOTATION_HOLDER_CONSTRUCTOR;
    private static final Method RUN_ANNOTATOR_METHOD;
    private static final Method CLEAR_METHOD;

    static {
        Class<?> sessionClass = null;
        Method createMethod = null;
        Class<?> holderClass = null;
        Constructor<?> holderConstructor = null;
        Method runAnnotatorMethod = null;
        Method clearMethod = null;

        try {
            // Load AnnotationSessionImpl class
            sessionClass = Class.forName("com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl");

            // Get AnnotationSessionImpl.create(PsiFile) method
            createMethod = sessionClass.getMethod("create", PsiFile.class);

            // Load AnnotationHolderImpl class
            holderClass = Class.forName("com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl");

            // Get AnnotationHolderImpl(AnnotationSession, boolean) constructor
            holderConstructor = holderClass.getConstructor(AnnotationSession.class, boolean.class);

            // Get runAnnotatorWithContext(PsiElement) method
            runAnnotatorMethod = holderClass.getMethod("runAnnotatorWithContext", PsiElement.class);

            // Get clear() method
            clearMethod = holderClass.getMethod("clear");

        } catch (Exception e) {
            LOGGER.error("Failed to initialize AnnotationHolderReflection. " +
                    "LSP diagnostics may not work correctly. " +
                    "This is expected if IntelliJ internal API has changed.", e);
        }

        ANNOTATION_SESSION_IMPL_CLASS = sessionClass;
        CREATE_SESSION_METHOD = createMethod;
        ANNOTATION_HOLDER_IMPL_CLASS = holderClass;
        ANNOTATION_HOLDER_CONSTRUCTOR = holderConstructor;
        RUN_ANNOTATOR_METHOD = runAnnotatorMethod;
        CLEAR_METHOD = clearMethod;
    }

    /**
     * Create an {@link AnnotationSession} using reflection.
     *
     * @param psiFile the PSI file
     * @return the annotation session, or null if reflection failed
     */
    @SuppressWarnings("unchecked")
    public static AnnotationSession createSession(@NotNull PsiFile psiFile) {
        if (CREATE_SESSION_METHOD == null) {
            return null;
        }

        try {
            return (AnnotationSession) CREATE_SESSION_METHOD.invoke(null, psiFile);
        } catch (Exception e) {
            LOGGER.error("Failed to create AnnotationSession via reflection", e);
            return null;
        }
    }

    /**
     * Create an {@link AnnotationHolder} using reflection.
     *
     * @param session the annotation session
     * @param batching whether batching is enabled
     * @return the annotation holder, or null if reflection failed
     */
    @SuppressWarnings("unchecked")
    public static AnnotationHolder createHolder(@NotNull AnnotationSession session, boolean batching) {
        if (ANNOTATION_HOLDER_CONSTRUCTOR == null) {
            return null;
        }

        try {
            return (AnnotationHolder) ANNOTATION_HOLDER_CONSTRUCTOR.newInstance(session, batching);
        } catch (Exception e) {
            LOGGER.error("Failed to create AnnotationHolder via reflection", e);
            return null;
        }
    }

    /**
     * Call {@code runAnnotatorWithContext(PsiElement)} on the annotation holder using reflection.
     *
     * @param holder the annotation holder (must be instance of AnnotationHolderImpl)
     * @param psiElement the PSI element
     */
    public static void runAnnotatorWithContext(@NotNull AnnotationHolder holder, @NotNull PsiElement psiElement) {
        if (RUN_ANNOTATOR_METHOD == null) {
            return;
        }

        try {
            RUN_ANNOTATOR_METHOD.invoke(holder, psiElement);
        } catch (Exception e) {
            LOGGER.error("Failed to call runAnnotatorWithContext via reflection", e);
        }
    }

    /**
     * Call {@code clear()} on the annotation holder using reflection.
     *
     * @param holder the annotation holder (must be instance of AnnotationHolderImpl)
     */
    public static void clear(@NotNull AnnotationHolder holder) {
        if (CLEAR_METHOD == null) {
            return;
        }

        try {
            CLEAR_METHOD.invoke(holder);
        } catch (Exception e) {
            LOGGER.error("Failed to call clear() via reflection", e);
        }
    }

    /**
     * Get an iterable over the annotations in the holder.
     * Note: AnnotationHolderImpl implements Iterable, so we can iterate over it directly.
     *
     * @param holder the annotation holder (must be instance of AnnotationHolderImpl)
     * @return an iterable over annotations (returns the holder itself cast to Iterable)
     */
    @SuppressWarnings("unchecked")
    public static Iterable<Annotation> asIterable(@NotNull AnnotationHolder holder) {
        // AnnotationHolderImpl implements Iterable<Annotation>, so we can cast it directly
        // No need for reflection here - the interface is public
        if (holder instanceof Iterable) {
            return (Iterable<Annotation>) holder;
        }
        return Collections.emptyList();
    }

    /**
     * Check if reflection initialization succeeded.
     *
     * @return true if all reflection setup succeeded, false otherwise
     */
    public static boolean isAvailable() {
        return ANNOTATION_SESSION_IMPL_CLASS != null
                && CREATE_SESSION_METHOD != null
                && ANNOTATION_HOLDER_IMPL_CLASS != null
                && ANNOTATION_HOLDER_CONSTRUCTOR != null
                && RUN_ANNOTATOR_METHOD != null
                && CLEAR_METHOD != null;
    }
}

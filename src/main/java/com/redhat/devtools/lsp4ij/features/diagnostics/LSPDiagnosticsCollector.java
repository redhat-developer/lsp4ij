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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.OpenedDocument;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects LSP diagnostics and converts them to IntelliJ {@link HighlightInfo}.
 *
 * <p>Diagnostics are already cached by the LSP layer, so this is a fast operation.</p>
 *
 * <p>Implementation based on JetBrains LSP: uses reflection to access internal AnnotationHolderImpl
 * to reuse existing {@link com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature#createAnnotation}
 * logic, then converts {@link Annotation} to {@link HighlightInfo}.</p>
 */
public class LSPDiagnosticsCollector {

    private final @NotNull PsiFile psiFile;

    public LSPDiagnosticsCollector(@NotNull PsiFile psiFile) {
        this.psiFile = psiFile;
    }

    @Nullable
    public List<HighlightInfo> collect() {

        List<HighlightInfo> highlights = new ArrayList<>();

        // Get all started language servers
        var project = psiFile.getProject();
        var servers = LanguageServiceAccessor.getInstance(project).getStartedServers();
        if (servers.isEmpty()) {
            return null;
        }

        // Get document
        Document document = LSPIJUtils.getDocument(psiFile);
        if (document == null) {
            return null;
        }

        // Create annotation session and holder to reuse existing createAnnotation() logic
        // Uses reflection to access internal AnnotationHolderImpl because no public alternative exists
        // for converting LSP diagnostics to HighlightInfo outside an Annotator.
        if (!AnnotationHolderReflection.isAvailable()) {
            return null;  // Reflection failed - IntelliJ API may have changed
        }

        AnnotationSession session = AnnotationHolderReflection.createSession(psiFile);
        if (session == null) {
            return null;
        }

        AnnotationHolder holder = AnnotationHolderReflection.createHolder(session, false);
        if (holder == null) {
            return null;
        }

        AnnotationHolderReflection.runAnnotatorWithContext(holder, psiFile);

        // Loop through language servers that report diagnostics for this file
        for (var ls : servers) {
            ProgressManager.checkCanceled();

            URI fileUri = FileUriSupport.getFileUri(psiFile.getVirtualFile(), ls.getClientFeatures());
            OpenedDocument openedDocument = ls.getOpenedDocument(fileUri);
            if (openedDocument != null) {
                // Get diagnostics (already cached, no blocking)
                var diagnosticsForServer = openedDocument.getDiagnosticsForServer();
                var clientFeatures = diagnosticsForServer.getClientFeatures();
                var diagnosticSupport = clientFeatures.getDiagnosticFeature();
                var codeActionFeature = clientFeatures.getCodeActionFeature();

                for (Diagnostic diagnostic : openedDocument.getDiagnostics()) {
                    ProgressManager.checkCanceled();

                    // Check if diagnostics are enabled
                    if (!diagnosticSupport.isEnabled(psiFile)) {
                        continue;
                    }

                    try {
                        // Get quick fixes if available
                        List<IntentionAction> fixes = Collections.emptyList();
                        if (codeActionFeature.isQuickFixesEnabled(psiFile)) {
                            fixes = diagnosticsForServer.getQuickFixesFor(diagnostic, psiFile);
                        }

                        // Reuse existing createAnnotation() logic
                        AnnotationHolderReflection.clear(holder);
                        diagnosticSupport.createAnnotation(diagnostic, document, fixes, holder);

                        // Convert annotations to HighlightInfo
                        for (Annotation annotation : AnnotationHolderReflection.asIterable(holder)) {
                            HighlightInfo info = convertAnnotationToHighlightInfo(annotation);
                            highlights.add(info);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        // Diagnostic range is invalid (document changed since server sent it)
                        // Skip this diagnostic silently
                    }
                }
            }
        }

        return highlights.isEmpty() ? null : highlights;
    }

    /**
     * Convert IntelliJ {@link Annotation} to {@link HighlightInfo}.
     * Based on JetBrains LSP implementation: {@code LspHighlightingApplier.convertAnnotationToHighlightInfo}.
     */
    @NotNull
    private static HighlightInfo convertAnnotationToHighlightInfo(@NotNull Annotation annotation) {
        HighlightInfoType type = toHighlightInfoType(annotation.getHighlightType(), annotation.getSeverity());
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type)
                .range(annotation.getStartOffset(), annotation.getEndOffset())
                .severity(annotation.getSeverity());

        // Message and tooltip
        String message = annotation.getMessage();
        if (message != null) {
            builder.description(message);
        }
        String tooltip = annotation.getTooltip();
        if (tooltip != null) {
            builder.escapedToolTip(tooltip);
        }

        // Text attributes
        TextAttributesKey textAttributes = annotation.getTextAttributes();
        if (textAttributes != null) {
            builder.textAttributes(textAttributes);
        }

        // Special flags
        if (annotation.isAfterEndOfLine()) {
            builder.endOfLine();
        }
        if (annotation.isFileLevelAnnotation()) {
            builder.fileLevelAnnotation();
        }

        // Gutter icon
        if (annotation.getGutterIconRenderer() != null) {
            builder.gutterIconRenderer(annotation.getGutterIconRenderer());
        }

        // Quick fixes
        List<Annotation.QuickFixInfo> quickFixes = annotation.getQuickFixes();
        if (quickFixes != null) {
            for (Annotation.QuickFixInfo fixInfo : quickFixes) {
                builder.registerFix(fixInfo.quickFix, null, null, fixInfo.textRange, fixInfo.key);
            }
        }

        return builder.createUnconditionally();
    }

    /**
     * Convert {@link ProblemHighlightType} to {@link HighlightInfoType}.
     * Based on JetBrains LSP implementation: {@code LspHighlightingApplier.toHighlightInfoType}.
     */
    @NotNull
    private static HighlightInfoType toHighlightInfoType(@NotNull ProblemHighlightType problemHighlightType,
                                                         @NotNull HighlightSeverity severity) {
        if (problemHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
            return HighlightInfoType.UNUSED_SYMBOL;
        }
        if (problemHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) {
            return HighlightInfoType.WRONG_REF;
        }
        if (problemHighlightType == ProblemHighlightType.LIKE_DEPRECATED) {
            return HighlightInfoType.DEPRECATED;
        }
        if (problemHighlightType == ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL) {
            return HighlightInfoType.MARKED_FOR_REMOVAL;
        }
        if (problemHighlightType == ProblemHighlightType.POSSIBLE_PROBLEM) {
            return HighlightInfoType.POSSIBLE_PROBLEM;
        }
        return HighlightInfo.convertSeverity(severity);
    }
}

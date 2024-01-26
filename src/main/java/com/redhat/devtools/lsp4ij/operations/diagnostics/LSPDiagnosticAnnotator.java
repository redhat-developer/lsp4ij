/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.operations.diagnostics;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPVirtualFileData;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.hint.LSPNavigationLinkHandler;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.operations.codeactions.LSPLazyCodeActionIntentionAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRelatedInformation;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * Intellij {@link ExternalAnnotator} implementation which get the current LSP diagnostics for a given file and translate
 * them into Intellij {@link com.intellij.lang.annotation.Annotation}.
 */
public class LSPDiagnosticAnnotator extends ExternalAnnotator<Boolean, Boolean> {

    @Nullable
    @Override
    public Boolean collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Override
    public @Nullable Boolean doAnnotate(Boolean result) {
        return result;
    }

    @Override
    public void apply(@NotNull PsiFile file, Boolean applyAnnotator, @NotNull AnnotationHolder holder) {
        if (!applyAnnotator) {
            return;
        }
        URI fileUri = LSPIJUtils.toUri(file);
        Document document = LSPIJUtils.getDocument(file.getVirtualFile());

        // Loop for language server which report diagnostics for the given file
        var servers = LanguageServiceAccessor.getInstance(file.getProject())
                .getStartedServers();
        for (var ls : servers) {
            LSPVirtualFileData data = ls.getLSPVirtualFileData(fileUri);
            if (data != null) {
                // The file is mapped with the current language server
                var ds = data.getDiagnosticsForServer();
                // Loop for LSP diagnostics to transform it to Intellij annotation.
                for (Diagnostic diagnostic : ds.getDiagnostics()) {
                    ProgressManager.checkCanceled();
                    createAnnotation(diagnostic, document, ds, holder);
                }
            }
        }
    }

    private static void createAnnotation(Diagnostic diagnostic, Document document, LSPDiagnosticsForServer diagnosticsForServer, AnnotationHolder holder) {
        // Get the text range from teh given LSP diagnostic range.
        // Since IJ cannot highlight an error when the start/end range offset are the same
        // the method LSPIJUtils.toTextRange is called with adjust, in other words when start/end range offset are the same:
        // - when the offset is at the end of the line, the method returns a text range with the same  offset,
        // and annotation must be created with Annotation#setAfterEndOfLine(true).
        // - when the offset is inside the line, the end offset is incremented.
        TextRange range = LSPIJUtils.toTextRange(diagnostic.getRange(), document, true);
        if (range == null) {
            // Language server reports invalid diagnostic, ignore it.
            return;
        }

        // Collect information required to create Intellij Annotations
        HighlightSeverity severity = SeverityMapping.toHighlightSeverity(diagnostic.getSeverity());
        String message = diagnostic.getMessage();

        // Create IntelliJ Annotation from the given LSP diagnostic
        AnnotationBuilder builder = holder
                .newAnnotation(severity, message)
                .tooltip(getToolTip(diagnostic))
                .range(range);
        if (range.getStartOffset() == range.getEndOffset()) {
            // Show the annotation at the end of line.
            builder.afterEndOfLine();
        }

        // Register lazy quick fixes
        List<LSPLazyCodeActionIntentionAction> fixes = diagnosticsForServer.getQuickFixesFor(diagnostic);
        for (IntentionAction fix : fixes) {
            builder.withFix(fix);
        }
        builder.create();
    }

    /**
     * Returns the annotation tooltip from the given LSP diagnostic.
     *
     * @param diagnostic the LSP diagnostic.
     * @return the annotation tooltip from the given LSP diagnostic.
     */
    private static String getToolTip(Diagnostic diagnostic) {
        // message
        StringBuilder tooltip = new StringBuilder("<html>");
        tooltip.append(StringUtil.escapeXmlEntities(diagnostic.getMessage()));
        // source
        tooltip.append("<span style=\"font: italic;\"> ");
        String source = diagnostic.getSource();
        if (StringUtils.isNotBlank(source)) {
            tooltip.append(source);
        }
        // error code
        Either<String, Integer> code = diagnostic.getCode();
        if (code != null) {
            String errorCode = code.isLeft() ? code.getLeft() : code.isRight() ? String.valueOf(code.getRight()) : null;
            if (StringUtils.isNotBlank(errorCode)) {
                tooltip.append("&nbsp(");
                String href = diagnostic.getCodeDescription() != null ? diagnostic.getCodeDescription().getHref() : null;
                addLink(errorCode, href, tooltip);
                tooltip.append(")");
            }
        }
        // Diagnostic related informations
        List<DiagnosticRelatedInformation> informations = diagnostic.getRelatedInformation();
        if (informations != null) {
            tooltip.append("<ul>");
            for (var information : informations) {
                String message = information.getMessage();
                tooltip.append("<li>");
                Location location = information.getLocation();
                if (location != null) {
                    String fileName = getFileName(location);
                    String fileUrl = LSPNavigationLinkHandler.toNavigationUrl(location);
                    addLink(fileName, fileUrl, tooltip);
                    tooltip.append(":&nbsp;");
                }
                tooltip.append(message);
                tooltip.append("</li>");
            }
            tooltip.append("</ul>");
        }
        tooltip.append("</span></html>");
        return tooltip.toString();
    }

    @NotNull
    private static String getFileName(Location location) {
        String fileUri = location.getUri();
        int index = fileUri.lastIndexOf('/');
        String fileName = fileUri.substring(index + 1);
        StringBuilder result = new StringBuilder(fileName);
        Range range = location.getRange();
        if (range != null) {
            result.append("(");
            result.append(range.getStart().getLine());
            result.append(":");
            result.append(range.getStart().getCharacter());
            result.append(", ");
            result.append(range.getEnd().getLine());
            result.append(":");
            result.append(range.getEnd().getCharacter());
            result.append(")");
        }
        return fileName;
    }

    private static void addLink(String text, String href, StringBuilder tooltip) {
        boolean hasHref = StringUtils.isNotBlank(href);
        if (hasHref) {
            tooltip.append("<a href=\"");
            tooltip.append(href);
            tooltip.append("\">");
        }
        tooltip.append(text);
        if (hasHref) {
            tooltip.append("</a>");
        }
    }

}
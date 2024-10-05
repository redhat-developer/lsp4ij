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
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.diagnostics.SeverityMapping;
import com.redhat.devtools.lsp4ij.hint.LSPNavigationLinkHandler;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LSP diagnostic feature.
 * <p>
 * The following code snippet demonstrates how to use this class to allow a language server to ignore some LSP diagnostic:
 * <pre>{@code
 * public class MyLSPDiagnosticFeature extends LSPDiagnosticFeature {
 *      @Override
 *     public @Nullable HighlightSeverity getHighlightSeverity(@NotNull Diagnostic diagnostic) {
 *         if (diagnostic.getCode() != null &&
 *                 diagnostic.getCode().isLeft() &&
 *                 "ignore".equals(diagnostic.getCode().getLeft())) {
 *             // return a null HighlightSeverity when LSP diagnostic code is equals
 *             // to 'ignore' to avoid creating an IntelliJ annotation
 *             return null;
 *         }
 *         return super.getHighlightSeverity(diagnostic);
 *     }
 * }
 * }</pre>
 * See the documentation of {@link #getHighlightSeverity(Diagnostic)} for more details.
 * <p>
 * Additional information is available on <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/LSPApi.md#lsp-diagnostic-feature">GitHub</a>
 */
@ApiStatus.Experimental
public class LSPDiagnosticFeature extends AbstractLSPDocumentFeature {

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return true;
    }

    /**
     * Create an IntelliJ annotation in the given holder by using given LSP diagnostic and fixes.
     * @param diagnostic the LSP diagnostic.
     * @param document the document.
     * @param fixes the fixes coming from LSP CodeAction.
     * @param holder the annotation holder where annotation must be registered.
     */
    public void createAnnotation(@NotNull Diagnostic diagnostic,
                                 @NotNull Document document,
                                 @NotNull List<IntentionAction> fixes,
                                 @NotNull AnnotationHolder holder) {

        // Get the text range from the given LSP diagnostic range.
        // Since IJ cannot highlight an error when the start/end range offset are the same
        // the method LSPIJUtils.toTextRange is called with adjust, in other words when start/end range offset are the same:
        // - when the offset is at the end of the line, the method returns a text range with the same  offset,
        // and annotation must be created with Annotation#setAfterEndOfLine(true).
        // - when the offset is inside the line, the end offset is incremented.
        TextRange range = LSPIJUtils.toTextRange(diagnostic.getRange(), document, null, true);
        if (range == null) {
            // Language server reports invalid diagnostic, ignore it.
            return;
        }

        HighlightSeverity severity = getHighlightSeverity(diagnostic);
        if (severity == null) {
            // Ignore the diagnostic
            return;
        }

        // Collect information required to create Intellij Annotations
        String message = getMessage(diagnostic);

        // Create IntelliJ Annotation from the given LSP diagnostic
        AnnotationBuilder builder = holder
                .newAnnotation(severity, message)
                .tooltip(getTooltip(diagnostic))
                .range(range);
        if (range.getStartOffset() == range.getEndOffset()) {
            // Show the annotation at the end of line.
            builder.afterEndOfLine();
        }

        // Update highlight type from the diagnostic tags
        ProblemHighlightType highlightType = getProblemHighlightType(diagnostic.getTags());
        if (highlightType != null) {
            builder.highlightType(highlightType);
        }

        // Register lazy quick fixes
        for (IntentionAction fix : fixes) {
            builder.withFix(fix);
        }
        builder.create();
    }

    /**
     * Returns the IntelliJ {@link HighlightSeverity} from the given diagnostic and null otherwise.
     *
     * <p>
     * If null is returned, the diagnostic will be ignored.
     * </p>
     *
     * @param diagnostic the LSP diagnostic.
     * @return the IntelliJ {@link HighlightSeverity} from the given diagnostic and null otherwise.
     */
    @Nullable
    public HighlightSeverity getHighlightSeverity(@NotNull Diagnostic diagnostic) {
        return SeverityMapping.toHighlightSeverity(diagnostic.getSeverity());
    }

    /**
     * Returns the message of the given diagnostic.
     *
     * @param diagnostic the LSP diagnostic.
     * @return the message of the given diagnostic.
     */
    @NotNull
    public String getMessage(@NotNull Diagnostic diagnostic) {
        return diagnostic.getMessage();
    }

    /**
     * Returns the annotation tooltip from the given LSP diagnostic.
     *
     * @param diagnostic the LSP diagnostic.
     * @return the annotation tooltip from the given LSP diagnostic.
     */
    @NotNull
    public String getTooltip(@NotNull Diagnostic diagnostic) {
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
        // Diagnostic related information
        List<DiagnosticRelatedInformation> information = diagnostic.getRelatedInformation();
        if (information != null) {
            tooltip.append("<ul>");
            for (var item : information) {
                String message = item.getMessage();
                tooltip.append("<li>");
                Location location = item.getLocation();
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
    private static String getFileName(@NotNull Location location) {
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

    /**
     * Returns the {@link ProblemHighlightType} from the given tags and null otherwise.
     *
     * @param tags the diagnostic tags.
     * @return the {@link ProblemHighlightType} from the given tags and null otherwise.
     */
    @Nullable
    public ProblemHighlightType getProblemHighlightType(@Nullable List<DiagnosticTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.contains(DiagnosticTag.Unnecessary)) {
            return ProblemHighlightType.LIKE_UNUSED_SYMBOL;
        }
        if (tags.contains(DiagnosticTag.Deprecated)) {
            return ProblemHighlightType.LIKE_DEPRECATED;
        }
        return null;
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        // Do nothing
    }
}

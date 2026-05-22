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
package com.redhat.devtools.lsp4ij.features.color;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.SequencePresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.picker.ColorListener;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.AbstractLSPInlayHintsProvider;
import com.redhat.devtools.lsp4ij.internal.PsiFileChangedException;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.ignoreAllExceptions;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP textDocument/colorInformation support.
 */
public class LSPColorProvider extends AbstractLSPInlayHintsProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPColorProvider.class);

    private enum ColorFormatType {
        HEX,
        HSL,
        RGB
    }

    private static class ColorPickerState {
        java.awt.Color lastAppliedColor;
        int formatIndex = -1;
        long requestVersion = 0;
        CompletableFuture<?> pendingRequest = null;
        boolean isApplyingChange = false;

        ColorPickerState(java.awt.Color initialColor) {
            this.lastAppliedColor = initialColor;
        }
    }

    @Override
    protected void doCollect(@NotNull PsiFile psiFile,
                                             @NotNull Editor editor,
                                             @NotNull PresentationFactory factory,
                                             @NotNull InlayHintsSink inlayHintsSink) {
        // Get LSP color information from cache or create them
        LSPColorSupport colorSupport = LSPFileSupport.getSupport(psiFile).getColorSupport();
        var params = new DocumentColorParams(new TextDocumentIdentifier());
        CompletableFuture<List<ColorData>> future = colorSupport.getColors(params);

        try {
            // Wait until the future is done and stop the wait if there are some ProcessCanceledException.
            waitUntilDone(future, psiFile);
            if (isDoneNormally(future)) {

                // Collect color information
                List<Pair<Integer, ColorData>> colors = new ArrayList<>();
                List<ColorData> data = future.getNow(Collections.emptyList());
                fillColor(editor.getDocument(), data, colors);

                // Render color information
                colors.stream()
                        .collect(Collectors.groupingBy(p -> p.first))
                        .forEach((offset, list) ->
                                inlayHintsSink.addInlineElement(
                                        offset,
                                        false,
                                        toPresentation(list, psiFile, factory, editor))
                        );
            }
        } catch (PsiFileChangedException e) {
            // The file content has changed, cancel the LSP textDocument/documentColor requests.
            colorSupport.cancel();
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (CancellationException ignore) {
        } catch (ExecutionException e) {
            LOGGER.error("Error while consuming LSP 'textDocument/documentColor' request", e);
        }
    }

    private static void fillColor(Document document, List<ColorData> data, List<Pair<Integer, ColorData>> colors) {
        for (var codeLensData : data) {
            int offset = LSPIJUtils.toOffset(codeLensData.color().getRange().getStart(), document);
            colors.add(Pair.create(offset, codeLensData));
        }
    }

    private InlayPresentation toPresentation(@NotNull List<Pair<Integer, ColorData>> elements,
                                             @NotNull PsiFile file,
                                             @NotNull PresentationFactory factory,
                                             @NotNull Editor editor) {
        List<InlayPresentation> presentations = new ArrayList<>();

        elements.forEach(p -> {
            ColorData colorData = p.second;
            Color lspColor = colorData.color().getColor();
            java.awt.Color awtColor = new java.awt.Color(
                    (float) lspColor.getRed(),
                    (float) lspColor.getGreen(),
                    (float) lspColor.getBlue(),
                    (float) lspColor.getAlpha()
            );

            // Create the color square presentation
            InlayPresentation colorSquare = new ColorInlayPresentation(awtColor, factory);

            // Make it clickable - wrap with reference
            InlayPresentation clickable = factory.referenceOnHover(colorSquare, (event, point) -> openColorPicker(awtColor, colorData, editor, file));

            presentations.add(clickable);
        });
        return new SequencePresentation(presentations);
    }

    private void openColorPicker(@NotNull java.awt.Color currentColor,
                                  @NotNull ColorData colorData,
                                  @NotNull Editor editor,
                                  @Nullable PsiFile file) {
        Project project = editor.getProject();
        if (project == null || file == null) return;

        Document document = editor.getDocument();
        Range range = colorData.color().getRange();

        // Get current text in the color range to detect original format
        int startOffset = LSPIJUtils.toOffset(range.getStart(), document);
        int endOffset = LSPIJUtils.toOffset(range.getEnd(), document);
        String originalText = document.getText(new com.intellij.openapi.util.TextRange(startOffset, endOffset));

        // Create a RangeMarker that will automatically track document changes
        RangeMarker rangeMarker = document.createRangeMarker(startOffset, endOffset);
        rangeMarker.setGreedyToLeft(true);
        rangeMarker.setGreedyToRight(true);

        final ColorPickerState state = new ColorPickerState(currentColor);

        // Detect original format by requesting presentations for current color
        Color currentLspColor = colorData.color().getColor();
        String uri = LSPIJUtils.toUri(file).toString();
        ColorPresentationParams initialParams = new ColorPresentationParams(
                new TextDocumentIdentifier(uri),
                currentLspColor,
                range
        );

        LSPColorPresentationSupport colorPresentationSupport = LSPFileSupport.getSupport(file).getColorPresentationSupport();
        colorPresentationSupport.cancel();

        CompletableFuture<Void> formatDetectionFuture = colorPresentationSupport.getColorPresentations(initialParams)
                .thenAcceptAsync(initialPresentations -> {
                    if (initialPresentations != null && !initialPresentations.isEmpty()) {
                        // Detect format from original text prefix
                        ColorFormatType formatType = detectFormatType(originalText);

                        // Find presentation matching the detected format type
                        for (int i = 0; i < initialPresentations.size(); i++) {
                            ColorPresentation presentation = initialPresentations.get(i);
                            String presentationText = presentation.getTextEdit() != null
                                    ? presentation.getTextEdit().getNewText()
                                    : presentation.getLabel();

                            ColorFormatType presentationType = detectFormatType(presentationText);

                            if (formatType == presentationType) {
                                state.formatIndex = i;
                                break;
                            }
                        }

                        // If no match found, default to index 0
                        if (state.formatIndex == -1) {
                            state.formatIndex = 0;
                        }
                    } else {
                        // No presentations available, use index 0
                        state.formatIndex = 0;
                    }
                })
                .exceptionally(ex -> {
                    // Fallback to rgb format on error
                    state.formatIndex = 0;
                    return null;
                });

        // Wait for format detection to complete, then open the color picker
        formatDetectionFuture.thenRunAsync(() -> {
        ColorListener colorListener = (selectedColor, source) -> {
            if (selectedColor != null && !selectedColor.equals(state.lastAppliedColor)) {
                state.lastAppliedColor = selectedColor;

                // Cancel previous request
                if (state.pendingRequest != null && !state.pendingRequest.isDone()) {
                    state.pendingRequest.cancel(true);
                }
                colorPresentationSupport.cancel();

                // Increment version to invalidate any in-flight responses
                long currentVersion = ++state.requestVersion;

                // Convert to LSP Color
                Color lspColor = new Color(
                        selectedColor.getRed() / 255.0,
                        selectedColor.getGreen() / 255.0,
                        selectedColor.getBlue() / 255.0,
                        selectedColor.getAlpha() / 255.0
                );

                // Wait until any pending modification is complete before reading the range
                waitForPendingModification(state, () -> {
                    // Get current range from marker (it follows document changes)
                    if (!rangeMarker.isValid()) {
                        return;
                    }

                    // Convert marker positions back to LSP Range for the request
                    Range currentRange = new Range(
                            LSPIJUtils.toPosition(rangeMarker.getStartOffset(), document),
                            LSPIJUtils.toPosition(rangeMarker.getEndOffset(), document)
                    );

                    // Request color presentations from LSP server
                    ColorPresentationParams params = new ColorPresentationParams(
                            new TextDocumentIdentifier(uri),
                            lspColor,
                            currentRange
                    );

                    CompletableFuture<List<ColorPresentation>> future = colorPresentationSupport.getColorPresentations(params);
                    state.pendingRequest = future;

                    future.thenAcceptAsync(presentations -> {
                                // Ignore obsolete responses
                                if (currentVersion != state.requestVersion) {
                                    return;
                                }

                                if (presentations != null && !presentations.isEmpty()) {
                                    // Use the same format as the original, fallback to rgb (index 0) if not available
                                    int detectedIndex = Math.max(state.formatIndex, 0);
                                    int indexToUse = detectedIndex < presentations.size() ? detectedIndex : 0;

                                    // Mark that we're applying a change
                                    state.isApplyingChange = true;
                                    applyColorPresentation(presentations.get(indexToUse), rangeMarker, editor, () -> {
                                        // Clear the flag when modification is complete
                                        state.isApplyingChange = false;
                                    });
                                }
                            })
                            .exceptionally(ignoreAllExceptions());
                });
            }
        };

            // Show standard IntelliJ color picker popup
            ApplicationManager.getApplication().invokeLater(() -> ColorChooserService.getInstance().showPopup(
                    project,
                    currentColor,
                    colorListener,
                    null, // show at mouse position
                    true  // show alpha channel
            ));
        });
    }

    /**
     * Detect color format type from text prefix.
     * @param text Color text (e.g., "#FF0000", "rgb(255, 0, 0)", "hsl(0, 100%, 50%)")
     * @return Format type: HEX, HSL, or RGB
     */
    private static ColorFormatType detectFormatType(String text) {
        if (text == null || text.isEmpty()) {
            return ColorFormatType.RGB;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("#")) {
            return ColorFormatType.HEX;
        } else if (trimmed.startsWith("hsl")) {
            return ColorFormatType.HSL;
        } else {
            return ColorFormatType.RGB;
        }
    }

    private void waitForPendingModification(ColorPickerState state, Runnable action) {
        if (state.isApplyingChange) {
            ApplicationManager.getApplication().invokeLater(() -> waitForPendingModification(state, action));
        } else {
            action.run();
        }
    }

    private void applyColorPresentation(@NotNull ColorPresentation presentation,
                                        @NotNull com.intellij.openapi.editor.RangeMarker rangeMarker,
                                        @NotNull Editor editor,
                                        @NotNull Runnable onComplete) {
        Document document = editor.getDocument();
        Project project = editor.getProject();
        if (project == null) {
            onComplete.run();
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    // Check if marker is still valid
                    if (!rangeMarker.isValid()) {
                        return;
                    }

                    // Apply main text edit
                    if (presentation.getTextEdit() != null) {
                        LSPIJUtils.applyEdits(editor, document, List.of(presentation.getTextEdit()));
                    } else {
                        // Per LSP spec: when textEdit is omitted, use the label
                        int startOffset = rangeMarker.getStartOffset();
                        int endOffset = rangeMarker.getEndOffset();
                        document.replaceString(startOffset, endOffset, presentation.getLabel());
                    }

                    // Apply additional edits if any
                    if (presentation.getAdditionalTextEdits() != null && !presentation.getAdditionalTextEdits().isEmpty()) {
                        LSPIJUtils.applyEdits(editor, document, presentation.getAdditionalTextEdits());
                    }
                });
            } finally {
                // Always clear the flag, even if an exception occurred
                onComplete.run();
            }
        });
    }

}

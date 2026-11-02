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
package com.redhat.devtools.lsp4ij.features.linkedEditingRange;

import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Edits the linked editing ranges of an editor together, as VS Code does and as IntelliJ edits the start and the end
 * tag of an XML element.
 * <p>
 * When the caret stops, the language servers of the file which support 'textDocument/linkedEditingRange' are asked for
 * the ranges linked at the caret. A change of the command inside the range with the caret is then copied into the
 * other ranges before the command finishes, so that they are undone together. Any other change, a typed text which
 * doesn't match the word pattern of the ranges, and a caret which leaves the range, drop the ranges.
 * <p>
 * It lives on the EDT, as the caret and the document of its editor.
 */
@ApiStatus.Internal
public final class LSPLinkedEditingRangeSynchronizer implements CaretListener, DocumentListener, CommandListener, Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPLinkedEditingRangeSynchronizer.class);

    private static final Key<LSPLinkedEditingRangeSynchronizer> SYNCHRONIZER_KEY = Key.create("lsp.linked.editing.range.synchronizer");

    // The time, in milliseconds, which the caret stays at an offset before the linked editing ranges are asked for
    private static final int REQUEST_DELAY = 100;

    // The most characters which matching a word pattern may read: a word pattern of the language server which
    // backtracks without end must not hang the EDT
    private static final int WORD_PATTERN_READ_LIMIT = 100_000;

    // The word pattern of the ranges when the language server gives none: VS Code's default word definition
    private static final Pattern DEFAULT_WORD_PATTERN = Pattern.compile("(-?\\d*\\.\\d\\w*)|([^`~!@#$%^&*()\\-=+\\[{\\]}\\\\|;:'\",.<>/?\\s]+)");

    private final @NotNull Editor editor;
    private final @NotNull Project project;
    private final @NotNull Alarm requestAlarm;

    // the linked ranges, the one with the caret first, or none
    private @NotNull List<RangeMarker> ranges = List.of();
    private @NotNull Pattern wordPattern = DEFAULT_WORD_PATTERN;
    // true when a change of the current command is in the range with the caret
    private boolean changed;
    // true while the other ranges are changed
    private boolean applying;
    // the requests of this editor, which another editor of the file doesn't cancel
    private @Nullable LSPLinkedEditingRangeSupport support;
    // completes once the result of the last request is applied or dropped
    private @Nullable CompletableFuture<Void> request;

    private LSPLinkedEditingRangeSynchronizer(@NotNull Editor editor, @NotNull Project project) {
        this.editor = editor;
        this.project = project;
        this.requestAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    }

    /**
     * Edits the linked editing ranges of the given editor together, until it is released.
     *
     * @param editor the editor.
     */
    static void install(@NotNull Editor editor) {
        Project project = editor.getProject();
        // the editor gets its file after it is created, its document has it before
        if (project == null || project.isDefault() || editor.isViewer()
                || FileDocumentManager.getInstance().getFile(editor.getDocument()) == null
                || editor.getUserData(SYNCHRONIZER_KEY) != null) {
            return;
        }
        var synchronizer = new LSPLinkedEditingRangeSynchronizer(editor, project);
        editor.getCaretModel().addCaretListener(synchronizer, synchronizer);
        editor.getDocument().addDocumentListener(synchronizer, synchronizer);
        ApplicationManager.getApplication().getMessageBus().connect(synchronizer).subscribe(CommandListener.TOPIC, synchronizer);
        editor.putUserData(SYNCHRONIZER_KEY, synchronizer);
        // the caret may stay where the editor opens
        synchronizer.requestAlarm.addRequest(synchronizer::requestLinkedEditingRanges, REQUEST_DELAY);
    }

    /**
     * Stops editing the linked editing ranges of the given editor together.
     *
     * @param editor the released editor.
     */
    static void uninstall(@NotNull Editor editor) {
        var synchronizer = editor.getUserData(SYNCHRONIZER_KEY);
        if (synchronizer != null) {
            Disposer.dispose(synchronizer);
        }
    }

    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
        if (applying) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        if (!ranges.isEmpty() && contains(ranges.get(0), offset)) {
            // the caret stays in the range which it edits
            return;
        }
        clearRanges();
        requestAlarm.cancelAllRequests();
        if (editor.getCaretModel().getCaretCount() == 1) {
            requestAlarm.addRequest(this::requestLinkedEditingRanges, REQUEST_DELAY);
        }
    }

    @Override
    public void caretAdded(@NotNull CaretEvent event) {
        // linked editing is for one caret only
        clearRanges();
        requestAlarm.cancelAllRequests();
    }

    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (applying || ranges.isEmpty()) {
            return;
        }
        var undoManager = UndoManager.getInstance(project);
        if (event.getDocument().isInBulkUpdate()
                || undoManager.isUndoInProgress() || undoManager.isRedoInProgress()
                || CommandProcessor.getInstance().getCurrentCommand() == null
                || !isInFirstRange(event)) {
            clearRanges();
            return;
        }
        changed = true;
    }

    @Override
    public void beforeCommandFinished(@NotNull CommandEvent event) {
        if (!changed) {
            return;
        }
        changed = false;
        if (ranges.isEmpty() || !ranges.stream().allMatch(RangeMarker::isValid)) {
            clearRanges();
            return;
        }
        Document document = editor.getDocument();
        String text = document.getText(ranges.get(0).getTextRange());
        if (!isWord(text, wordPattern)) {
            clearRanges();
            return;
        }
        List<RangeMarker> others = ranges.subList(1, ranges.size());
        Runnable apply = () -> {
            for (RangeMarker other : others) {
                if (!text.equals(document.getText(other.getTextRange()))) {
                    document.replaceString(other.getStartOffset(), other.getEndOffset(), text);
                }
            }
        };
        applying = true;
        try {
            WriteAction.run(() -> {
                // a change outside the guard of an open completion popup closes it
                LookupEx lookup = LookupManager.getActiveLookup(editor);
                if (lookup != null) {
                    lookup.performGuardedChange(apply);
                } else {
                    apply.run();
                }
            });
        } finally {
            applying = false;
        }
    }

    private void requestLinkedEditingRanges() {
        if (editor.isDisposed() || project.isDisposed() || editor.getCaretModel().getCaretCount() != 1) {
            return;
        }
        Document document = editor.getDocument();
        PsiFile file = ReadAction.compute(() -> {
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            return psiFile != null
                    && LanguageServersRegistry.getInstance().isFileSupported(psiFile)
                    && ProjectIndexingManager.canExecuteLSPFeature(psiFile) == ExecuteLSPFeatureStatus.NOW
                    && LSPLinkedEditingRangeSupport.isLinkedEditingRangeAvailable(psiFile) ? psiFile : null;
        });
        if (file == null) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        long modificationStamp = document.getModificationStamp();
        var params = new LSPLinkedEditingRangeParams(new TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, document), offset);
        var applied = new CompletableFuture<Void>();
        request = applied;
        if (support == null || support.getFile() != file) {
            if (support != null) {
                support.cancel();
            }
            support = new LSPLinkedEditingRangeSupport(file);
        }
        support.getLinkedEditingRanges(params)
                .whenComplete((result, error) -> {
                    if (result == null) {
                        // cancelled, or the language server failed, which LSP4IJ has reported
                        applied.complete(null);
                        return;
                    }
                    ApplicationManager.getApplication().invokeLater(() -> {
                                try {
                                    setRanges(result, offset, modificationStamp);
                                } finally {
                                    applied.complete(null);
                                }
                            },
                            ModalityState.stateForComponent(editor.getComponent()),
                            o -> {
                                if (editor.isDisposed()) {
                                    applied.complete(null);
                                    return true;
                                }
                                return false;
                            });
                });
    }

    private void setRanges(@NotNull List<LinkedEditingRanges> result, int offset, long modificationStamp) {
        Document document = editor.getDocument();
        if (editor.isDisposed() || document.getModificationStamp() != modificationStamp
                || editor.getCaretModel().getCaretCount() != 1 || editor.getCaretModel().getOffset() != offset) {
            // the document or the caret have changed since the request
            return;
        }
        clearRanges();
        for (LinkedEditingRanges linkedEditingRanges : result) {
            List<TextRange> textRanges = toTextRanges(linkedEditingRanges, offset, document);
            if (textRanges != null) {
                List<RangeMarker> markers = new ArrayList<>(textRanges.size());
                for (TextRange textRange : textRanges) {
                    RangeMarker marker = document.createRangeMarker(textRange);
                    // text typed at either end of a range belongs to it
                    marker.setGreedyToLeft(true);
                    marker.setGreedyToRight(true);
                    markers.add(marker);
                }
                ranges = List.copyOf(markers);
                wordPattern = toWordPattern(linkedEditingRanges.getWordPattern());
                return;
            }
        }
    }

    /**
     * Returns the text ranges of the given linked editing ranges, the one with the caret first, or null when they are
     * not ranges which can be edited together at the caret: fewer than two, out of the document, overlapping or
     * touching (a text typed between two ranges would grow both), with different texts, or none with the caret.
     */
    private static @Nullable List<TextRange> toTextRanges(@NotNull LinkedEditingRanges linkedEditingRanges,
                                                          int offset,
                                                          @NotNull Document document) {
        var lspRanges = linkedEditingRanges.getRanges();
        if (lspRanges == null || lspRanges.size() < 2) {
            return null;
        }
        List<TextRange> textRanges = lspRanges.stream()
                .map(range -> range != null ? LSPIJUtils.toTextRange(range, document) : null)
                .toList();
        if (textRanges.contains(null)) {
            return null;
        }
        TextRange first = textRanges.stream()
                .filter(textRange -> textRange.containsOffset(offset))
                .findFirst()
                .orElse(null);
        if (first == null) {
            return null;
        }
        String text = document.getText(first);
        List<TextRange> sorted = textRanges.stream()
                .sorted(Comparator.comparingInt(TextRange::getStartOffset))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (!text.equals(document.getText(sorted.get(i)))
                    || (i > 0 && sorted.get(i - 1).getEndOffset() >= sorted.get(i).getStartOffset())) {
                return null;
            }
        }
        List<TextRange> ordered = new ArrayList<>(textRanges.size());
        ordered.add(first);
        textRanges.stream()
                .filter(textRange -> textRange != first)
                .forEach(ordered::add);
        return ordered;
    }

    private static @NotNull Pattern toWordPattern(@Nullable String wordPattern) {
        if (wordPattern == null || wordPattern.isEmpty()) {
            return DEFAULT_WORD_PATTERN;
        }
        try {
            return Pattern.compile(wordPattern);
        } catch (PatternSyntaxException e) {
            LOGGER.warn("Cannot use the word pattern '{}' of linked editing ranges", wordPattern, e);
            return DEFAULT_WORD_PATTERN;
        }
    }

    /**
     * Returns true if the first match of the word pattern in the text is the whole text, as VS Code checks it. An empty
     * text is a word, so that a range can be deleted and typed again. A match which reads too many characters is not
     * a word.
     */
    static boolean isWord(@NotNull String text, @NotNull Pattern wordPattern) {
        var matcher = wordPattern.matcher(new ReadLimitedText(text, WORD_PATTERN_READ_LIMIT));
        try {
            int matchLength = matcher.find() ? matcher.end() - matcher.start() : 0;
            return matchLength == text.length();
        } catch (ReadLimitExceededException e) {
            LOGGER.warn("Stopped matching the word pattern '{}' of linked editing ranges, after {} characters", wordPattern, WORD_PATTERN_READ_LIMIT);
            return false;
        }
    }

    /**
     * A text which can be read a limited number of times, which bounds the work of a regular expression on it.
     */
    private static final class ReadLimitedText implements CharSequence {

        private final @NotNull String text;
        private int readsLeft;

        private ReadLimitedText(@NotNull String text, int readLimit) {
            this.text = text;
            this.readsLeft = readLimit;
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(int index) {
            if (--readsLeft < 0) {
                throw new ReadLimitExceededException();
            }
            return text.charAt(index);
        }

        @Override
        public @NotNull CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @Override
        public @NotNull String toString() {
            return text;
        }
    }

    private static final class ReadLimitExceededException extends RuntimeException {
        private ReadLimitExceededException() {
            super(null, null, false, false);
        }
    }

    private boolean isInFirstRange(@NotNull DocumentEvent event) {
        RangeMarker first = ranges.get(0);
        return first.isValid()
                && event.getOffset() >= first.getStartOffset()
                && event.getOffset() + event.getOldLength() <= first.getEndOffset();
    }

    private static boolean contains(@NotNull RangeMarker range, int offset) {
        return range.isValid() && range.getStartOffset() <= offset && offset <= range.getEndOffset();
    }

    private void clearRanges() {
        ranges.forEach(RangeMarker::dispose);
        ranges = List.of();
        wordPattern = DEFAULT_WORD_PATTERN;
        changed = false;
    }

    @Override
    public void dispose() {
        if (support != null) {
            // releases this synchronizer from a request which the language server hasn't answered
            support.cancel();
            support = null;
        }
        clearRanges();
        editor.putUserData(SYNCHRONIZER_KEY, null);
    }

    /**
     * Returns the text ranges which are edited together, the one with the caret first.
     */
    @TestOnly
    @NotNull List<TextRange> getRanges() {
        return ranges.stream()
                .filter(RangeMarker::isValid)
                .map(RangeMarker::getTextRange)
                .toList();
    }

    /**
     * Asks at once for the linked editing ranges at the caret, instead of after the delay.
     *
     * @return a future which completes once the result is applied or dropped, or null when nothing is asked.
     */
    @TestOnly
    @Nullable CompletableFuture<Void> requestNow() {
        requestAlarm.cancelAllRequests();
        request = null;
        requestLinkedEditingRanges();
        return request;
    }

    @TestOnly
    static @Nullable LSPLinkedEditingRangeSynchronizer getSynchronizer(@NotNull Editor editor) {
        return editor.getUserData(SYNCHRONIZER_KEY);
    }
}

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
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.ExecuteLSPFeatureStatus;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.InlineValueContext;
import org.eclipse.lsp4j.InlineValueParams;
import org.eclipse.lsp4j.InlineValueVariableLookup;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.runCancellableReadAction;

/**
 * {@code LSPDebugVariablePositionProvider} gives the positions of the variables which the language servers of the
 * stack frame's file name with LSP
 * <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue">textDocument/inlineValue</a>,
 * when they support it ({@code inlineValueProvider}).
 * <p>
 * The inline values are asked for the range from the start of the file to the end of the line where the program
 * stopped, with the id of the DAP stack frame and the location where it stopped as context. Each variable lookup
 * gives its variable a position, and since IntelliJ shows one value per variable, a variable is shown where a
 * language server names it last, before or on the stopped line. The text and the evaluatable expression inline values
 * are ignored.
 * </p>
 * <p>
 * The language servers are asked without a read action, in {@link #loadContext(DebugVariableContext)}, and are waited
 * for a limited time: the variables they don't name, because they didn't answer in time, failed or don't support
 * inline values, get their positions from the next provider.
 * </p>
 */
public class LSPDebugVariablePositionProvider implements DebugVariablePositionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDebugVariablePositionProvider.class);

    /**
     * The default time to wait for the language servers to answer.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    // The variable lookups which the language servers answered for the stack frame of a context, up to the stopped line
    private static final Key<List<InlineValueVariableLookup>> VARIABLE_LOOKUPS = Key.create("lsp4ij.dap.inlineValue.variableLookups");

    private static final Comparator<Range> BY_START = Comparator
            .comparingInt((Range range) -> range.getStart().getLine())
            .thenComparingInt(range -> range.getStart().getCharacter());

    private final @NotNull Duration timeout;

    /**
     * Constructs a {@code LSPDebugVariablePositionProvider} which waits {@link #DEFAULT_TIMEOUT} for the language
     * servers to answer.
     */
    public LSPDebugVariablePositionProvider() {
        this(DEFAULT_TIMEOUT);
    }

    /**
     * Constructs a {@code LSPDebugVariablePositionProvider}.
     *
     * @param timeout the time to wait for the language servers to answer.
     */
    public LSPDebugVariablePositionProvider(@NotNull Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public @NotNull CompletableFuture<Void> loadContext(@NotNull DebugVariableContext context) {
        DAPStackFrame stackFrame = context.getStackFrame();
        Range stoppedLocation = getStoppedLocation(stackFrame.getStackFrame());
        int stoppedLine = stoppedLocation.getStart().getLine();
        // Read the file and the range to ask for in a read action, which waits for nothing
        InlineValueRequest request = runCancellableReadAction(() -> createRequest(context, stoppedLine),
                stackFrame.getClient().getProject());
        if (request == null) {
            return CompletableFuture.completedFuture(null);
        }
        var cancellationSupport = new CancellationSupport();
        var inlineValueContext = new InlineValueContext(stackFrame.getFrameId(), stoppedLocation);
        var variableLookups = getVariableLookups(request.file(), request.range(), inlineValueContext, cancellationSupport);
        return waitForVariableLookups(variableLookups, timeout, cancellationSupport)
                .thenAccept(answered -> {
                    var lookups = getNamedVariableLookups(answered, request.text(), stoppedLine);
                    if (!lookups.isEmpty()) {
                        context.putUserData(VARIABLE_LOOKUPS, lookups);
                    }
                });
    }

    private static @Nullable InlineValueRequest createRequest(@NotNull DebugVariableContext context, int stoppedLine) {
        VirtualFile file = context.getFile();
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, context.getStackFrame().getClient().getProject());
        if (psiFile == null || ProjectIndexingManager.canExecuteLSPFeature(psiFile) != ExecuteLSPFeatureStatus.NOW) {
            // No language server can answer for the file now
            return null;
        }
        Editor editor = context.getEditor();
        Document document = editor != null ? editor.getDocument() : LSPIJUtils.getDocument(file);
        if (document == null) {
            return null;
        }
        return new InlineValueRequest(psiFile, document.getImmutableCharSequence(), getRange(document, stoppedLine));
    }

    @Override
    public void configureContext(@NotNull DebugVariableContext context) {
        // The variable lookups are kept as the language servers answered them, in loadContext
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition(@NotNull Variable variable,
                                                       @NotNull DebugVariableContext context) {
        var lookups = context.getUserData(VARIABLE_LOOKUPS);
        String variableName = variable.getName();
        if (lookups == null || variableName == null) {
            return null;
        }
        Range range = findVariableRange(lookups, variableName);
        if (range == null) {
            // The language servers don't name the variable: the next provider gives its position
            return null;
        }
        // As the highlighter provider does, the position is the end of the variable name
        return XDebuggerUtil.getInstance()
                .createPosition(context.getFile(), range.getStart().getLine(), range.getEnd().getCharacter());
    }

    /**
     * Returns the LSP location where the program stopped in the given DAP stack frame.
     *
     * @param stackFrame the DAP stack frame.
     * @return the LSP location where the program stopped in the given DAP stack frame.
     */
    static @NotNull Range getStoppedLocation(@NotNull StackFrame stackFrame) {
        // DAP lines and columns start at 1 (see DebugAdapterDescriptor), LSP ones at 0. As VS Code does, the end of
        // the location is its start when the DAP server gives no end
        int line = Math.max(stackFrame.getLine() - 1, 0);
        int character = Math.max(stackFrame.getColumn() - 1, 0);
        int endLine = stackFrame.getEndLine() != null ? Math.max(stackFrame.getEndLine() - 1, 0) : line;
        int endCharacter = stackFrame.getEndColumn() != null ? Math.max(stackFrame.getEndColumn() - 1, 0) : character;
        return new Range(new Position(line, character), new Position(endLine, endCharacter));
    }

    /**
     * Returns the range to ask the inline values for: from the start of the document to the end of the stopped line.
     *
     * @param document    the document.
     * @param stoppedLine the line where the program stopped.
     * @return the range to ask the inline values for.
     */
    static @NotNull Range getRange(@NotNull Document document, int stoppedLine) {
        var start = new Position(0, 0);
        int lineCount = document.getLineCount();
        if (lineCount == 0) {
            return new Range(start, start);
        }
        int line = Math.min(stoppedLine, lineCount - 1);
        int character = document.getLineEndOffset(line) - document.getLineStartOffset(line);
        return new Range(start, new Position(line, character));
    }

    /**
     * Returns the variable lookups of the inline values which the language servers of the given file answer with
     * 'textDocument/inlineValue', when they support it.
     *
     * @param file                the Psi file.
     * @param range               the range to ask the inline values for.
     * @param context             the stack frame and the location where the program stopped.
     * @param cancellationSupport the cancellation support of the requests.
     * @return the variable lookups of the inline values which the language servers of the given file answer.
     */
    static @NotNull CompletableFuture<List<InlineValueVariableLookup>> getVariableLookups(@NotNull PsiFile file,
                                                                                          @NotNull Range range,
                                                                                          @NotNull InlineValueContext context,
                                                                                          @NotNull CancellationSupport cancellationSupport) {
        var project = file.getProject();
        if (project.isDefault() || project.isDisposed()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return LanguageServiceAccessor.getInstance(project)
                .getLanguageServers(file,
                        f -> f.getInlineValueFeature().isEnabled(file),
                        f -> f.getInlineValueFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have inline value capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedStage(Collections.emptyList());
                    }

                    // Collect list of textDocument/inlineValue future for each language servers
                    List<CompletableFuture<List<InlineValueVariableLookup>>> lookupsPerServerFutures = languageServers
                            .stream()
                            .map(languageServer -> getVariableLookupsFor(file, range, context, languageServer, cancellationSupport))
                            .toList();

                    // Merge list of textDocument/inlineValue future in one future which return the list of variable lookups
                    return CompletableFutures.mergeInOneFuture(lookupsPerServerFutures, cancellationSupport);
                });
    }

    private static @NotNull CompletableFuture<List<InlineValueVariableLookup>> getVariableLookupsFor(@NotNull PsiFile file,
                                                                                                     @NotNull Range range,
                                                                                                     @NotNull InlineValueContext context,
                                                                                                     @NotNull LanguageServerItem languageServer,
                                                                                                     @NotNull CancellationSupport cancellationSupport) {
        // Each language server has its own params, with its own Uri of the file
        var textDocument = new TextDocumentIdentifier(FileUriSupport.toString(file.getVirtualFile(), languageServer.getClientFeatures()));
        var params = new InlineValueParams(textDocument, range, context);
        return cancellationSupport.execute(languageServer
                        .getTextDocumentService()
                        .inlineValue(params), languageServer, LSPRequestConstants.TEXT_DOCUMENT_INLINE_VALUE)
                .thenApply(LSPDebugVariablePositionProvider::toVariableLookups);
    }

    /**
     * Returns the variable lookups of the given inline values. IntelliJ shows the values of the variables of the stack
     * frame: the text and the evaluatable expression inline values are ignored.
     *
     * @param inlineValues the inline values which a language server answered, which may be null.
     * @return the variable lookups of the given inline values.
     */
    static @NotNull List<InlineValueVariableLookup> toVariableLookups(@Nullable List<?> inlineValues) {
        if (inlineValues == null) {
            // textDocument/inlineValue may return null
            return Collections.emptyList();
        }
        // lsp4j declares InlineValue elements, but its InlineValueResponseAdapter reads each one as an Either3 which
        // isn't an InlineValue: read them as Either3
        return inlineValues
                .stream()
                .filter(Either3.class::isInstance)
                .map(inlineValue -> (Either3<?, ?, ?>) inlineValue)
                .filter(Either3::isSecond)
                .map(Either3::getSecond)
                .filter(InlineValueVariableLookup.class::isInstance)
                .map(InlineValueVariableLookup.class::cast)
                .toList();
    }

    /**
     * Waits, for the given time at most, for the variable lookups. The returned future never fails: the variable
     * lookups are empty when they failed, or when they don't come in time, and the requests which haven't been
     * answered are then cancelled.
     *
     * @param variableLookups     the variable lookups which the language servers answer.
     * @param timeout             the time to wait for the variable lookups.
     * @param cancellationSupport the cancellation support of the requests.
     * @return the variable lookups, or empty variable lookups when they don't come in time or failed.
     */
    static @NotNull CompletableFuture<List<InlineValueVariableLookup>> waitForVariableLookups(@NotNull CompletableFuture<List<InlineValueVariableLookup>> variableLookups,
                                                                                              @NotNull Duration timeout,
                                                                                              @NotNull CancellationSupport cancellationSupport) {
        // A copy times out, so that an answer which comes too late completes nothing that was already used
        return variableLookups
                .copy()
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((lookups, error) -> {
                    if (error == null) {
                        return lookups != null ? lookups : Collections.<InlineValueVariableLookup>emptyList();
                    }
                    // The language servers which haven't answered stop computing the inline values
                    cancellationSupport.cancel();
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        LOGGER.debug("The language servers didn't answer 'textDocument/inlineValue' in {} ms", timeout.toMillis());
                    } else if (!(cause instanceof CancellationException)) {
                        LOGGER.warn("Error while getting the inline values", cause);
                    }
                    return Collections.<InlineValueVariableLookup>emptyList();
                });
    }

    /**
     * Returns the given variable lookups which name a variable before or on the stopped line, each with the name of
     * the variable to look up.
     *
     * @param lookups     the variable lookups which the language servers answered.
     * @param text        the text of the document, to read the name of a variable lookup which has none.
     * @param stoppedLine the line where the program stopped.
     * @return the given variable lookups which name a variable before or on the stopped line.
     */
    static @NotNull List<InlineValueVariableLookup> getNamedVariableLookups(@NotNull List<InlineValueVariableLookup> lookups,
                                                                            @NotNull CharSequence text,
                                                                            int stoppedLine) {
        return lookups
                .stream()
                .filter(lookup -> isRange(lookup.getRange()) && lookup.getRange().getStart().getLine() <= stoppedLine)
                .map(lookup -> withVariableName(lookup, text))
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isRange(@Nullable Range range) {
        return range != null && range.getStart() != null && range.getEnd() != null;
    }

    private static @Nullable InlineValueVariableLookup withVariableName(@NotNull InlineValueVariableLookup lookup,
                                                                        @NotNull CharSequence text) {
        if (StringUtils.isNotBlank(lookup.getVariableName())) {
            return lookup;
        }
        // A variable lookup without a variable name looks up the variable which its range names
        Range range = lookup.getRange();
        int start = StringUtil.lineColToOffset(text, range.getStart().getLine(), range.getStart().getCharacter());
        int end = StringUtil.lineColToOffset(text, range.getEnd().getLine(), range.getEnd().getCharacter());
        if (start < 0 || end < start || end > text.length()) {
            return null;
        }
        String variableName = text.subSequence(start, end).toString().trim();
        if (variableName.isEmpty()) {
            return null;
        }
        return new InlineValueVariableLookup(range, lookup.isCaseSensitiveLookup(), variableName);
    }

    /**
     * Returns the range of the variable lookup which names the given variable nearest to the stop, which is the last
     * one, and null when none names it.
     *
     * @param lookups      the variable lookups up to the stopped line.
     * @param variableName the name of the variable.
     * @return the range of the variable lookup which names the given variable nearest to the stop, and null otherwise.
     */
    static @Nullable Range findVariableRange(@NotNull List<InlineValueVariableLookup> lookups,
                                             @NotNull String variableName) {
        return lookups
                .stream()
                .filter(lookup -> lookup.isCaseSensitiveLookup()
                        ? variableName.equals(lookup.getVariableName())
                        : variableName.equalsIgnoreCase(lookup.getVariableName()))
                .map(InlineValueVariableLookup::getRange)
                .max(BY_START)
                .orElse(null);
    }

    private record InlineValueRequest(@NotNull PsiFile file, @NotNull CharSequence text, @NotNull Range range) {
    }
}

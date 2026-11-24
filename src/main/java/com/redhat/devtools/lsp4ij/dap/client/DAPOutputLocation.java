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
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPLocations;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * The source location which a debug adapter gives the output of an 'output' event, which the debug console shows as a
 * link after the output, as VS Code shows it at the right of the output. Clicking the link opens the location:
 * <ul>
 *     <li>the 'source' and the 'line' where the output was produced, with its 'column', if any: the link shows the
 *     name of the source and the line, such as {@code app.js:12}, and opens the local file of the source's path, or the
 *     text which the debug adapter gives for its source reference, at that line and column, as the source of a stack
 *     frame is opened;</li>
 *     <li>else the 'locationReference' of the logged value, which says where the value is declared, such as a function:
 *     the link asks the debug adapter where the reference points to, with the 'locations' request, only when it is
 *     clicked, so that the output is printed at once. The reference has the lifetime of a 'variablesReference': it is
 *     sent only while the program hasn't stopped or resumed since the output.</li>
 * </ul>
 * The lines and columns start at 1, as LSP4IJ asks the debug adapter for them (linesStartAt1, columnsStartAt1).
 */
final class DAPOutputLocation implements HyperlinkInfo {

    private final @NotNull DAPClient client;
    private final @NotNull String text;
    // Where the output was produced, or null for the location reference of the logged value
    private final @Nullable Source source;
    private final @Nullable Integer line;
    private final @Nullable Integer column;
    // The location reference of the logged value, used without a source, and the execution epoch of the output, the
    // lifetime of the reference
    private final int locationReference;
    private final int executionEpoch;

    private DAPOutputLocation(@NotNull DAPClient client,
                              @NotNull String text,
                              @Nullable Source source,
                              @Nullable Integer line,
                              @Nullable Integer column,
                              int locationReference,
                              int executionEpoch) {
        this.client = client;
        this.text = text;
        this.source = source;
        this.line = line;
        this.column = column;
        this.locationReference = locationReference;
        this.executionEpoch = executionEpoch;
    }

    /**
     * Returns the source location of the given 'output' event, which the debug console shows as a link after the
     * output: the source and the line where the output was produced, if the event has both and the source can be
     * opened, or else the location reference of the logged value, if the event has one.
     *
     * @param client the DAP client which received the event, in the execution epoch of the event.
     * @param args   the 'output' event.
     * @return the source location of the output, or null if the event has none.
     */
    static @Nullable DAPOutputLocation of(@NotNull DAPClient client, @NotNull OutputEventArguments args) {
        Source source = args.getSource();
        Integer line = args.getLine();
        // As in VS Code, a source needs a line, and 0 is no location reference
        if (source != null && line != null && line > 0 && DAPLocations.canBeOpened(source)) {
            return new DAPOutputLocation(client, DAPStackFrame.getValidSourceName(source) + ":" + line, source, line,
                    args.getColumn(), 0, 0);
        }
        Integer reference = args.getLocationReference();
        if (reference != null && reference > 0) {
            return new DAPOutputLocation(client, DAPBundle.message("dap.output.valueSource"), null, null, null,
                    reference, client.getExecutionEpoch());
        }
        return null;
    }

    /**
     * Returns the text of the link, such as {@code app.js:12} for a source and a line.
     *
     * @return the text of the link.
     */
    @NotNull String getText() {
        return text;
    }

    /**
     * Opens the source location, when the link is clicked, or reports in the debug session why it can't. The location
     * is found off the EDT, since finding a local file may refresh the VFS, and LSP4J writes a request on the thread
     * which sends it.
     *
     * @param project the project of the debug console.
     */
    @Override
    public void navigate(@NotNull Project project) {
        // A source reference and a location reference are valid only in the debug session which gave them
        if ((source == null || DAPLocations.hasSourceReference(source)) && client.getSession().isStopped()) {
            report(DAPBundle.message("dap.output.location.ended"));
            return;
        }
        CompletableFuture.supplyAsync(() -> source != null ? open(project, source, line, column) : openLocation(project),
                        AppExecutorUtil.getAppExecutorService())
                .thenCompose(Function.identity())
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ?
                            error.getCause() : error;
                    report(DAPBundle.message("dap.output.location.failed", cause.getMessage()));
                    return null;
                });
    }

    /**
     * Asks the debug adapter where the location reference of the logged value points to, with the 'locations' request,
     * if the program hasn't stopped or resumed since the output, and opens that location, or reports why it can't. It
     * must be called off the EDT.
     *
     * @return the future, completed once the location is about to be opened, or once why it can't be is reported. It
     * fails with the error of the debug adapter, or when the program has run since the output.
     */
    private @NotNull CompletableFuture<@Nullable Void> openLocation(@NotNull Project project) {
        return client.sendInExecutionEpoch(executionEpoch, () -> client.locations(locationReference),
                        DAPBundle.message("dap.output.location.stale"))
                .thenComposeAsync(location -> {
                    Source located = DAPLocations.getSource(location);
                    if (located == null) {
                        report(DAPBundle.message("dap.output.location.none"));
                        return CompletableFuture.completedFuture(null);
                    }
                    return open(project, located, location.getLine(), location.getColumn());
                }, AppExecutorUtil.getAppExecutorService());
    }

    /**
     * Opens the given source at the given line and column, on the EDT, or reports why it can't. It must be called off
     * the EDT.
     *
     * @return the future, completed once the source is about to be opened, or once why it can't be is reported.
     */
    private @NotNull CompletableFuture<@Nullable Void> open(@NotNull Project project,
                                                            @NotNull Source source,
                                                            @Nullable Integer line,
                                                            @Nullable Integer column) {
        return DAPLocations.getSourcePosition(client, source, line, column)
                .thenAccept(position -> {
                    if (position != null) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                position.createNavigatable(project).navigate(true), project.getDisposed());
                    } else {
                        String shown = StringUtils.isNotBlank(source.getPath()) ?
                                source.getPath() : DAPStackFrame.getValidSourceName(source);
                        report(DAPBundle.message("dap.output.location.notFound", shown));
                    }
                });
    }

    private void report(@NotNull @Nls String message) {
        client.getSession().reportMessage(message, MessageType.WARNING);
    }
}

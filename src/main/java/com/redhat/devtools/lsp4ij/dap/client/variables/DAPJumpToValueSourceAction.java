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
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPSourceReferencePosition;
import com.redhat.devtools.lsp4ij.dap.client.files.LocalSourceFiles;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.LocationsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getValidFilePath;

/**
 * IntelliJ action of the Variables and Watches views, "Jump to Value Source", which opens the source of the selected
 * value as the DAP server locates it, such as the definition of the function which the value is. It asks the DAP
 * server where the value's 'valueLocationReference' points to, with the 'locations' request, and opens that source at
 * that line and column, as VS Code does when the user clicks a value which it shows as a link. It is shown only for a
 * value which has a 'valueLocationReference', and enabled only while the debug session is suspended: the reference is
 * valid only until the program resumes, as the 'variablesReference' of the value.
 */
public class DAPJumpToValueSourceAction extends AnAction implements DumbAware {

    public static final @NotNull String ID = "DAP.JumpToValueSource";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        DAPValue value = getValue(e.getDataContext());
        e.getPresentation().setVisible(value != null);
        e.getPresentation().setEnabled(value != null && isSessionSuspended(value.getClient()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DAPValue value = getValue(e.getDataContext());
        Integer reference = value != null ? getValueLocationReference(value) : null;
        if (reference != null && isSessionSuspended(value.getClient())) {
            jumpToValueSource(value, reference);
        }
    }

    /**
     * Returns the DAP value selected in the Variables or Watches view if it has a 'valueLocationReference', and null
     * otherwise.
     *
     * @param dataContext the data context of the view.
     * @return the selected DAP value if it has a 'valueLocationReference', and null otherwise.
     */
    static @Nullable DAPValue getValue(@NotNull DataContext dataContext) {
        return DAPBreakOnValueChangeAction.getSelectedValue(dataContext) instanceof DAPValue value
                && getValueLocationReference(value) != null ? value : null;
    }

    /**
     * Returns the 'valueLocationReference' of the variable which the given value shows, such as the value of a lazy
     * variable once the user asked for it, or null if it has none. As in VS Code, 0 is no reference.
     *
     * @param value the DAP value.
     * @return the 'valueLocationReference' of the value, or null if it has none.
     */
    static @Nullable Integer getValueLocationReference(@NotNull DAPValue value) {
        Integer reference = value.getShownVariable().getValueLocationReference();
        return reference != null && reference > 0 ? reference : null;
    }

    /**
     * Returns true if the debug session of the given client hasn't ended and is suspended, and false otherwise.
     *
     * @param client the DAP client of a value.
     * @return true if the debug session of the client hasn't ended and is suspended, and false otherwise.
     */
    private static boolean isSessionSuspended(@NotNull DAPClient client) {
        var session = client.getSession();
        return client.getDebugProtocolServer() != null && !session.isStopped() && session.isSuspended();
    }

    /**
     * Asks the DAP server of the given value where the value's location reference points to, with the 'locations'
     * request, off the EDT, then opens that source on the EDT, or reports in the debug session why it can't. The
     * reference of a value of an earlier stop, such as an Evaluate result kept across a step, isn't sent: it is valid only
     * until the program resumes.
     *
     * @param value     the DAP value, whose client is that of the DAP session which gave the value, such as a child
     *                  session started with 'startDebugging'.
     * @param reference the 'valueLocationReference' of the value.
     */
    static void jumpToValueSource(@NotNull DAPValue value, int reference) {
        DAPClient client = value.getClient();
        String name = value.getName();
        int epoch = value.getStackFrame().getExecutionEpoch();
        // A location reference has the lifetime of a 'variablesReference', the stop of the value's frame. Sent off the
        // EDT, as LSP4J writes a request on the thread which sends it
        CompletableFuture.supplyAsync(() -> client.sendInExecutionEpoch(epoch, () -> client.locations(reference),
                        DAPBundle.message("dap.variables.valueSource.stale")), AppExecutorUtil.getAppExecutorService())
                .thenCompose(Function.identity())
                .thenComposeAsync(location -> open(client, name, location), AppExecutorUtil.getAppExecutorService())
                .exceptionally(error -> {
                    report(client, DAPBundle.message("dap.variables.valueSource.failed", name,
                            getCause(error).getMessage()));
                    return null;
                });
    }

    /**
     * Opens the source of a location which the DAP server answered to 'locations', or reports why it can't. It runs off
     * the EDT, since finding a local file may refresh the VFS, and opens the source on the EDT.
     *
     * @param client   the DAP client which asked for the location.
     * @param name     the name of the value whose source is opened.
     * @param location the location which the DAP server answered, or null if it answered none.
     * @return the future, completed once the source is about to be opened, or once why it can't be is reported.
     */
    private static @NotNull CompletableFuture<@Nullable Void> open(@NotNull DAPClient client,
                                                                   @NotNull String name,
                                                                   @Nullable LocationsResponse location) {
        Source source = location != null ? location.getSource() : null;
        if (source == null || (!hasSourceReference(source) && StringUtils.isBlank(source.getPath()))) {
            report(client, DAPBundle.message("dap.variables.valueSource.none", name));
            return CompletableFuture.completedFuture(null);
        }
        XSourcePosition position = getSourcePosition(client, source, location.getLine(), location.getColumn());
        return whenResolved(position)
                .thenAccept(resolved -> {
                    if (resolved) {
                        navigate(client.getProject(), position);
                    } else {
                        String shown = StringUtils.isNotBlank(source.getPath()) ?
                                source.getPath() : DAPStackFrame.getValidSourceName(source);
                        report(client, DAPBundle.message("dap.variables.valueSource.notFound", name, shown));
                    }
                });
    }

    /**
     * Returns the position at the given line and column of a source which the DAP server gave, whose text is shown as
     * that of a stack frame's source: a source with a source reference in the text which the DAP server gives for it,
     * which is asked for again, and another in its local file. It must be called off the EDT, since finding a local
     * file may refresh the VFS.
     *
     * @param client the DAP client of the DAP server which gave the source.
     * @param source the source, with a path or a source reference.
     * @param line   the 1-based line, as LSP4IJ asks the DAP server for lines (linesStartAt1), or null for the first
     *               line.
     * @param column the 1-based column, as LSP4IJ asks the DAP server for columns (columnsStartAt1), or null for the
     *               start of the line.
     * @return the position, or null if the local file of the source is not found.
     */
    static @Nullable XSourcePosition getSourcePosition(@NotNull DAPClient client,
                                                       @NotNull Source source,
                                                       @Nullable Integer line,
                                                       @Nullable Integer column) {
        int lineIndex = line != null ? Math.max(line - 1, 0) : 0;
        int columnIndex = column != null ? Math.max(column - 1, 0) : 0;
        if (hasSourceReference(source)) {
            // The text of a source with a source reference is asked for even if the source has a path
            var file = DAPFileRegistry.getInstance().getOrCreateDAPFile(client.getConfigName(),
                    DAPStackFrame.getValidSourceName(source), client.getProject());
            return new DAPSourceReferencePosition(file, source.getSourceReference(), lineIndex, columnIndex, client);
        }
        Path path = getValidFilePath(source);
        VirtualFile file = path != null ? LocalSourceFiles.findFile(path) : null;
        return file != null ? XDebuggerUtil.getInstance().createPosition(file, lineIndex, columnIndex) : null;
    }

    /**
     * Returns a future which completes with true once the given position is known, and with false if it isn't: the
     * position in the text of a source with a source reference is known once the DAP server gave the text.
     */
    private static @NotNull CompletableFuture<Boolean> whenResolved(@Nullable XSourcePosition position) {
        if (position instanceof DAPSourceReferencePosition deferred) {
            return deferred.whenResolved();
        }
        return CompletableFuture.completedFuture(position != null);
    }

    private static void navigate(@NotNull Project project, @NotNull XSourcePosition position) {
        ApplicationManager.getApplication().invokeLater(() -> position.createNavigatable(project).navigate(true),
                project.getDisposed());
    }

    private static void report(@NotNull DAPClient client, @NotNull @Nls String message) {
        client.getSession().reportMessage(message, MessageType.WARNING);
    }

    private static boolean hasSourceReference(@NotNull Source source) {
        return source.getSourceReference() != null && source.getSourceReference() > 0;
    }

    private static @NotNull Throwable getCause(@NotNull Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}

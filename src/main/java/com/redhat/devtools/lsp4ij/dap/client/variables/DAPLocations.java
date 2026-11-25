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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
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
import java.util.function.Function;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getValidFilePath;

/**
 * The 'locations' request of the location references of a DAP value, such as the 'valueLocationReference' which "Jump
 * to Value Source" opens and the 'declarationLocationReference' which "Jump to Source" opens, and the source position
 * of its answer: a position in a local file, or in the text which the DAP server gives for a source reference. The
 * debug console opens the source location of an 'output' event with it too.
 */
public final class DAPLocations {

    private DAPLocations() {
    }

    /**
     * Asks the DAP server of the given value where a location reference of the value points to, with the 'locations'
     * request. A location reference has the lifetime of a 'variablesReference', the stop of the value's frame: the
     * request is sent only while that stop lasts. It is sent off the calling thread, such as the EDT, as LSP4J writes a
     * request on the thread which sends it.
     *
     * @param value        the DAP value, whose client is that of the DAP session which gave the value, such as a child
     *                     session started with 'startDebugging'.
     * @param reference    the location reference of the value.
     * @param endedMessage the message of the failure of the future when the stop of the value has ended.
     * @return the future of the location, which is null when the DAP server answers without one or when the client is not
     * connected to the DAP server; it fails with the error of the DAP server, or with the given message when the stop of
     * the value has ended.
     */
    static @NotNull CompletableFuture<@Nullable LocationsResponse> locate(@NotNull DAPValue value,
                                                                         int reference,
                                                                         @NotNull @Nls String endedMessage) {
        DAPClient client = value.getClient();
        int epoch = value.getStackFrame().getExecutionEpoch();
        return CompletableFuture.supplyAsync(() -> client.sendInExecutionEpoch(epoch, () -> client.locations(reference),
                        endedMessage), AppExecutorUtil.getAppExecutorService())
                .thenCompose(Function.identity());
    }

    /**
     * Returns the source of a location which the DAP server answered to 'locations' if it can be opened, that is if it
     * has a source reference or a path, and null otherwise.
     *
     * @param location the location which the DAP server answered, or null if it answered none.
     * @return the source of the location if it can be opened, and null otherwise.
     */
    public static @Nullable Source getSource(@Nullable LocationsResponse location) {
        Source source = location != null ? location.getSource() : null;
        return source != null && canBeOpened(source) ? source : null;
    }

    /**
     * Returns true if a source which the DAP server gave can be opened, that is if it has a source reference or a path,
     * and false otherwise.
     *
     * @param source the source which the DAP server gave.
     * @return true if the source has a source reference or a path, and false otherwise.
     */
    public static boolean canBeOpened(@NotNull Source source) {
        return hasSourceReference(source) || StringUtils.isNotBlank(source.getPath());
    }

    /**
     * Returns the future of the source position of a location which the DAP server answered to 'locations', once the
     * position is known. It must be called off the EDT, since finding a local file may refresh the VFS.
     *
     * @param client   the DAP client of the DAP server which answered the location.
     * @param location the location which the DAP server answered, or null if it answered none.
     * @return the future of the source position, which is null when the location has no source which can be opened, when
     * the local file of the source is not found, or when the DAP server gives no text for its source reference.
     */
    static @NotNull CompletableFuture<@Nullable XSourcePosition> getSourcePosition(@NotNull DAPClient client,
                                                                                  @Nullable LocationsResponse location) {
        Source source = getSource(location);
        if (source == null) {
            return CompletableFuture.completedFuture(null);
        }
        return getSourcePosition(client, source, location.getLine(), location.getColumn());
    }

    /**
     * Returns the future of the position at the given line and column of a source which the DAP server gave, once the
     * position is known, whose text is shown as that of a stack frame's source: a source with a source reference in the
     * text which the DAP server gives for it, which is asked for again, and another in its local file. It must be called
     * off the EDT, since finding a local file may refresh the VFS.
     *
     * @param client the DAP client of the DAP server which gave the source.
     * @param source the source, with a path or a source reference.
     * @param line   the 1-based line, as LSP4IJ asks the DAP server for lines (linesStartAt1), or null for the first
     *               line.
     * @param column the 1-based column, as LSP4IJ asks the DAP server for columns (columnsStartAt1), or null for the
     *               start of the line.
     * @return the future of the position, which is null when the local file of the source is not found, or when the DAP
     * server gives no text for its source reference.
     */
    public static @NotNull CompletableFuture<@Nullable XSourcePosition> getSourcePosition(@NotNull DAPClient client,
                                                                                         @NotNull Source source,
                                                                                         @Nullable Integer line,
                                                                                         @Nullable Integer column) {
        int lineIndex = line != null ? Math.max(line - 1, 0) : 0;
        int columnIndex = column != null ? Math.max(column - 1, 0) : 0;
        if (hasSourceReference(source)) {
            // The text of a source with a source reference is asked for even if the source has a path, and the position
            // in it is known once the DAP server gave the text
            var file = DAPFileRegistry.getInstance().getOrCreateDAPFile(client.getConfigName(),
                    source.getSourceReference(), DAPStackFrame.getValidSourceName(source), client.getProject());
            var position = new DAPSourceReferencePosition(file, source.getSourceReference(), lineIndex, columnIndex, client);
            // A source whose text the DAP server doesn't give can't be opened: its document would be empty, or stale
            return position.whenResolved().thenApply(resolved -> resolved && position.isTextLoaded() ? position : null);
        }
        Path path = getValidFilePath(source);
        VirtualFile file = path != null ? LocalSourceFiles.findFile(path) : null;
        return CompletableFuture.completedFuture(file != null ?
                XDebuggerUtil.getInstance().createPosition(file, lineIndex, columnIndex) : null);
    }

    /**
     * Returns true if the given source has a source reference, whose text the DAP server gives, and false otherwise.
     *
     * @param source the source which the DAP server gave.
     * @return true if the source has a source reference, and false otherwise.
     */
    public static boolean hasSourceReference(@NotNull Source source) {
        return source.getSourceReference() != null && source.getSourceReference() > 0;
    }
}

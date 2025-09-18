/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileNavigator;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XSourcePositionEx;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a source position whose actual location (line and offset) is resolved asynchronously.
 * <p>
 * This class is used in the context of the Debug Adapter Protocol (DAP) to handle scenarios where
 * the source content might not be immediately available:
 * <ul>
 *     <li>The source comes from a {@code SourceReference} provided by the DAP.</li>
 *     <li>The source is dynamically generated, e.g., assembly instructions for disassembly view.</li>
 * </ul>
 * <p>
 * Once the source content is loaded and the line is resolved, this position can be used for navigation,
 * highlighting, and other editor features.
 *
 * @param <T> the type of parameters required to load the source (e.g., DAP request parameters)
 * @param <F> the type of VirtualFile handled by this position
 */
public abstract class DeferredSourcePosition<T, F extends VirtualFile> implements XSourcePositionEx {

    /**
     * Flow to notify when the position has been successfully resolved.
     * <p>
     * Initially set to {@code false}. When the source is loaded and the line is resolved,
     * the value becomes {@code true}.
     */
    private final MutableStateFlow<Boolean> resolvedFlow = StateFlowKt.MutableStateFlow(false);

    /** The virtual file associated with this position. */
    private final F file;

    /** Modification count at the time of creation, used to detect changes in the file. */
    private final long modificationCount;

    /** Resolved line number once the source has been loaded, or {@code null} if not yet resolved. */
    private Integer line;

    /** Lazy value that computes the offset only when needed, once the line is known. */
    private NotNullLazyValue<Integer> offset;

    /**
     * Future representing the asynchronous task responsible for loading the source
     * and resolving the corresponding line number.
     */
    private final @NotNull CompletableFuture<Void> loadFuture;

    /**
     * Creates a deferred source position that will load and resolve the position asynchronously.
     *
     * @param parameters the parameters required to load the source
     * @param file       the virtual file associated with this position
     * @param client     the DAP client used to fetch source or disassembly data
     */
    public DeferredSourcePosition(@NotNull T parameters,
                                  @NotNull F file,
                                  @NotNull DAPClient client) {
        this.file = file;
        this.modificationCount = file.getModificationCount();

        // Start the asynchronous process to load the source and resolve the line.
        loadFuture = loadAndResolveLineAsync(parameters, file, client)
                .thenAccept(line -> {
                    this.line = line;
                    // Compute the offset lazily, based on the resolved line
                    this.offset = NotNullLazyValue.atomicLazy(() -> ReadAction.compute(() -> {
                        int column = 0; // Default column to 0 for now, may be extended later
                        Document document = FileDocumentManager.getInstance().getDocument(file);
                        if (document == null) {
                            return -1;
                        } else {
                            int l = Math.max(0, line);
                            int c = Math.max(0, column);
                            int offset = l < document.getLineCount() ? document.getLineStartOffset(l) + c : -1;
                            if (offset >= document.getTextLength()) {
                                offset = document.getTextLength() - 1;
                            }
                            return offset;
                        }
                    }));
                    resolvedFlow.setValue(true); // Mark as resolved
                });
    }

    /**
     * Loads the source (from a DAP source reference or disassembly) and resolves the
     * line number corresponding to the current position.
     * <p>
     * This method is asynchronous and returns a future that completes when the line is known.
     *
     * @param parameters the parameters required to load the source
     * @param file       the virtual file to associate with the loaded content
     * @param client     the DAP client used to fetch the source or disassembly
     * @return a future that will complete with the resolved line number
     */
    protected abstract CompletableFuture<Integer> loadAndResolveLineAsync(@NotNull T parameters,
                                                                          @NotNull F file,
                                                                          @NotNull DAPClient client);

    /**
     * @return the resolved line number, or {@code -1} if not yet resolved
     */
    @Override
    public int getLine() {
        return isResolved() ? line : -1;
    }

    /**
     * @return the resolved offset, or {@code -1} if not yet resolved
     */
    @Override
    public int getOffset() {
        return isResolved() ? offset.get() : -1;
    }

    /**
     * @return the file associated with this source position
     */
    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    /**
     * Creates a navigatable object that will wait for the source position to be resolved
     * before navigating to it.
     *
     * @param project the current project
     * @return a {@link Navigatable} that can handle deferred navigation
     */
    @Override
    public @NotNull Navigatable createNavigatable(@NotNull Project project) {
        return new DeferredNavigatable(project, this, loadFuture);
    }

    /**
     * Returns a flow that indicates whether the position is resolved.
     * <p>
     * Can be used to update the UI reactively as soon as the position is available.
     */
    @Override
    public @NotNull Flow<Boolean> getPositionUpdateFlow() {
        return resolvedFlow;
    }

    /**
     * @return true if the source and line number are resolved, false otherwise
     */
    private boolean isResolved() {
        return resolvedFlow.getValue();
    }

    /**
     * @return the modification count captured when this position was created
     */
    public long getModificationCount() {
        return modificationCount;
    }

    /**
     * A deferred navigatable descriptor that waits for the source position to be resolved
     * before navigating in the editor.
     * <p>
     * This is used so that navigation attempts (e.g., user clicks) do not fail
     * if the source is still being loaded.
     */
    public class DeferredNavigatable extends OpenFileDescriptor {

        /** The associated source position. */
        private final XSourcePosition position;

        /** Future used to wait for the position resolution before navigating. */
        private final CompletableFuture<Void> loadFuture;

        private DeferredNavigatable(@NotNull Project project,
                                    @NotNull XSourcePosition position,
                                    @NotNull CompletableFuture<Void> future) {
            super(project, position.getFile());
            this.position = position;
            this.loadFuture = future;
        }

        /**
         * Navigates to the resolved position.
         * <p>
         * If the position is not ready yet, it will wait for the future to complete first.
         */
        @Override
        public void navigate(boolean requestFocus) {
            if (isResolved()) {
                doNavigate(requestFocus);
            } else {
                loadFuture.thenAccept(unused -> doNavigate(requestFocus));
            }
        }

        private void doNavigate(boolean requestFocus) {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(getProject(), getFile(), position.getOffset());
            FileNavigator.getInstance().navigate(descriptor, requestFocus);
        }

        /**
         * Navigates in a specific editor to the resolved position.
         * <p>
         * If the position is not ready yet, it will wait for the future to complete first.
         */
        @Override
        public void navigateIn(@NotNull Editor e) {
            if (isResolved()) {
                doNavigateIn(e);
            } else {
                loadFuture.thenAccept(unused -> doNavigateIn(e));
            }
        }

        private void doNavigateIn(@NotNull Editor e) {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(getProject(), getFile(), position.getOffset());
            navigateInEditor(descriptor, e);
        }

        /**
         * @return true if navigation is possible (file is valid)
         */
        @Override
        public boolean canNavigate() {
            return this.position.getFile().isValid();
        }

        /**
         * @return true if navigation to the source is possible
         */
        @Override
        public boolean canNavigateToSource() {
            return this.canNavigate();
        }
    }
}

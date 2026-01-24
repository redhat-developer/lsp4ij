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
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local file-based {@link com.intellij.xdebugger.XSourcePosition} that resolves {@link VirtualFile} off the EDT.
 *
 * LocalFileSystem.findFileByPath() can touch PersistentFS and triggers SlowOperations warnings on the EDT.
 * This implementation returns a lightweight placeholder until the VFS lookup completes on a pooled thread.
 */
public final class DeferredLocalFileSourcePosition extends DeferredSourcePositionBase {

    private final @NotNull Path filePath;
    private final int line;
    private volatile @Nullable VirtualFile placeholderFile;

    private final AtomicBoolean resolutionStarted = new AtomicBoolean(false);
    private volatile @Nullable VirtualFile resolvedFile;
    private volatile int offset = -1;

    public DeferredLocalFileSourcePosition(@NotNull Path filePath, int line) {
        this.filePath = filePath;
        this.line = line;

        startResolveAsync();
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public @NotNull VirtualFile getFile() {
        VirtualFile file = resolvedFile;
        if (file != null) {
            return file;
        }

        startResolveAsync();

        if (!ApplicationManager.getApplication().isDispatchThread()) {
            resolveNow();
            file = resolvedFile;
            if (file != null) {
                return file;
            }
        }

        VirtualFile placeholder = placeholderFile;
        if (placeholder != null) {
            return placeholder;
        }
        String name = filePath.getFileName() != null ? filePath.getFileName().toString() : filePath.toString();
        placeholder = new LightVirtualFile(name);
        placeholderFile = placeholder;
        return placeholder;
    }

    private void startResolveAsync() {
        if (!resolutionStarted.compareAndSet(false, true)) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                resolveNow();
            } finally {
                resolveFuture.complete(null);
            }
        });
    }

    private void resolveNow() {
        if (resolvedFile != null) {
            return;
        }
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath.toString());
        if (file == null || !file.isValid()) {
            return;
        }
        int resolvedOffset = ReadAction.compute(() -> computeOffset(file, line));
        resolvedFile = file;
        offset = resolvedOffset;
        markResolved();
        resolveFuture.complete(null);
    }

    private static int computeOffset(@NotNull VirtualFile file, int line) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
            return -1;
        }
        int l = Math.max(0, line);
        int offset = l < document.getLineCount() ? document.getLineStartOffset(l) : -1;
        if (offset >= document.getTextLength()) {
            offset = document.getTextLength() - 1;
        }
        return offset;
    }
}

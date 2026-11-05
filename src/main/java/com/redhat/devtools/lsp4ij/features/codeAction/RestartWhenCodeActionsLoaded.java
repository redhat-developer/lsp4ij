/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Restarts the highlighting of a file once the code actions that the EDT found still loading have loaded.
 * <p>
 * The EDT never waits for a textDocument/codeAction answer: IntelliJ asks the lazy intentions for their text there,
 * when the lightbulb and the intention popup sort them, and a wait lasts as long as the language server takes. The
 * intentions are then not available yet, and the restarted intention pass, which reads the loaded code actions, shows
 * them.
 * <p>
 * A file's highlighting restarts only when the EDT asked while an answer was pending, and then once for all the
 * requests of the file that the EDT found pending, when the last of them completes, if one completed normally. It
 * does not restart a file that changed since the EDT last found one pending, as the edit restarted its highlighting
 * already and that pass sends requests of its own, nor a file that no editor shows. The restarted pass finds the
 * requests done and sends no other, so a restart never leads to another.
 */
public class RestartWhenCodeActionsLoaded {

    // The requests of each file that the EDT found pending and that have not completed; guarded by itself
    private static final Map<Key, Pending> PENDING = new HashMap<>();

    // The restarts so far
    private static final AtomicInteger RESTARTS = new AtomicInteger();

    // The request whose completion is counted for its file; guarded by this
    private @Nullable CompletableFuture<?> scheduled;

    /**
     * Restarts the highlighting of the given file once the given request and the other requests of the file that the
     * EDT found pending have completed, unless the request is already counted.
     *
     * @param request the textDocument/codeAction request that the EDT did not wait for.
     * @param project the project.
     * @param file    the file whose code actions the request loads.
     */
    public void schedule(@NotNull CompletableFuture<?> request,
                         @NotNull Project project,
                         @NotNull VirtualFile file) {
        synchronized (this) {
            if (scheduled == request) {
                return;
            }
            scheduled = request;
        }
        var key = new Key(project, file);
        long stamp = stamp(file);
        synchronized (PENDING) {
            var pending = PENDING.computeIfAbsent(key, k -> new Pending());
            pending.requests++;
            pending.stamp = stamp;
        }
        request.whenComplete((result, failure) -> completed(key, failure == null));
    }

    private static void completed(@NotNull Key key, boolean loaded) {
        synchronized (PENDING) {
            var pending = PENDING.get(key);
            if (pending == null) {
                return;
            }
            pending.loaded |= loaded;
            if (--pending.requests > 0) {
                return;
            }
            PENDING.remove(key);
            if (pending.loaded) {
                // Submitted while the file is still pending, so that the restart is known once it is not
                long stamp = pending.stamp;
                ReadAction.nonBlocking((Callable<Void>) () -> {
                            restart(key, stamp);
                            return null;
                        })
                        .coalesceBy(RestartWhenCodeActionsLoaded.class, key)
                        .expireWith(key.project())
                        .submit(AppExecutorUtil.getAppExecutorService());
            }
        }
    }

    private static void restart(@NotNull Key key, long stamp) {
        var file = key.file();
        var project = key.project();
        if (!file.isValid() || stamp(file) != stamp || !FileEditorManager.getInstance(project).isFileOpen(file)) {
            // The file changed, and its edit restarted its highlighting, or no editor shows it
            return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
            RESTARTS.incrementAndGet();
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
    }

    /**
     * Returns the modification stamp of the document of the given file, or -1 when it has none loaded, as when no
     * editor shows it.
     */
    private static long stamp(@NotNull VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        return document != null ? document.getModificationStamp() : -1;
    }

    /**
     * Returns true while a request of the given file that the EDT found pending has not completed, and false once
     * none has, when a restart that their completion makes is already submitted.
     */
    @TestOnly
    public static boolean isPending(@NotNull Project project, @NotNull VirtualFile file) {
        synchronized (PENDING) {
            return PENDING.containsKey(new Key(project, file));
        }
    }

    /**
     * Returns the number of highlighting restarts so far.
     */
    @TestOnly
    public static int getRestartCount() {
        return RESTARTS.get();
    }

    /**
     * A file of a project.
     */
    private record Key(@NotNull Project project, @NotNull VirtualFile file) {
    }

    /**
     * The requests of a file that the EDT found pending: how many have not completed, the modification stamp of its
     * document when the EDT last found one pending, and whether one completed normally.
     */
    private static final class Pending {
        private int requests;
        private long stamp;
        private boolean loaded;
    }
}

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
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
@Service(Service.Level.PROJECT)
public final class RestartWhenCodeActionsLoadedManager {

    private final Project project;

    // The requests of each file that the EDT found pending and that have not completed; guarded by itself
    private final Map<VirtualFile, Pending> pendingFiles = new HashMap<>();

    // The restarts so far
    private final AtomicInteger restarts = new AtomicInteger();

    public RestartWhenCodeActionsLoadedManager(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static RestartWhenCodeActionsLoadedManager getInstance(@NotNull Project project) {
        return project.getService(RestartWhenCodeActionsLoadedManager.class);
    }

    /**
     * Restarts the highlighting of the given file once the given request and the other requests of the file that the
     * EDT found pending have completed, unless the request is already counted.
     *
     * @param request the textDocument/codeAction request that the EDT did not wait for.
     * @param file    the file whose code actions the request loads.
     */
    public void schedule(@NotNull CompletableFuture<?> request,
                         @NotNull VirtualFile file) {
        long stamp = stamp(file);
        synchronized (pendingFiles) {
            var pending = pendingFiles.computeIfAbsent(file, k -> new Pending());
            if (!pending.requests.add(request)) {
                return;
            }
            pending.stamp = stamp;
        }
        request.whenComplete((result, failure) -> completed(file, request, failure == null));
    }

    private void completed(@NotNull VirtualFile file, @NotNull CompletableFuture<?> request, boolean loaded) {
        synchronized (pendingFiles) {
            var pending = pendingFiles.get(file);
            if (pending == null || !pending.requests.remove(request)) {
                return;
            }
            pending.loaded |= loaded;
            if (!pending.requests.isEmpty()) {
                return;
            }
            pendingFiles.remove(file);
            if (pending.loaded) {
                // Submitted while the file is still pending, so that the restart is known once it is not
                long stamp = pending.stamp;
                ReadAction.nonBlocking((Callable<Void>) () -> {
                            restart(file, stamp);
                            return null;
                        })
                        .coalesceBy(this, file)
                        .expireWith(project)
                        .submit(AppExecutorUtil.getAppExecutorService());
            }
        }
    }

    private void restart(@NotNull VirtualFile file, long stamp) {
        if (!file.isValid() || stamp(file) != stamp || !FileEditorManager.getInstance(project).isFileOpen(file)) {
            // The file changed, and its edit restarted its highlighting, or no editor shows it
            return;
        }
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
            restarts.incrementAndGet();
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
    public boolean isPending(@NotNull VirtualFile file) {
        synchronized (pendingFiles) {
            return pendingFiles.containsKey(file);
        }
    }

    /**
     * Returns the number of highlighting restarts of this project so far.
     */
    @TestOnly
    public int getRestartCount() {
        return restarts.get();
    }

    /**
     * The requests of a file that the EDT found pending: those that have not completed, the modification stamp of its
     * document when the EDT last found one pending, and whether one completed normally.
     */
    private static final class Pending {
        private final Set<CompletableFuture<?>> requests = new HashSet<>();
        private long stamp;
        private boolean loaded;
    }
}

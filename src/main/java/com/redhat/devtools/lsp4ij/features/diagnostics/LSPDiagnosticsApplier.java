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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service that applies LSP diagnostics reactively when the server sends publishDiagnostics.
 *
 * <p>This is the ONLY way diagnostics are applied in normal editor mode (not batch).
 * This avoids flicker when typing because diagnostics are always up-to-date.</p>
 */
@Service(Service.Level.PROJECT)
public final class LSPDiagnosticsApplier implements Disposable {

    public static volatile int GROUP_ID = -1;

    // The highlighters that the diagnostics applied last time, kept by the markup model of the document and the project,
    // which owns them anyway
    private static final Key<List<RangeHighlighter>> APPLIED_HIGHLIGHTERS = Key.create("lsp4ij.diagnostics.appliedHighlighters");

    private final Project project;

    // Refreshes requested before a highlighting pass registrar set GROUP_ID, by file. Checking GROUP_ID
    // and adding a refresh under this lock means applyPendingRefreshes() cannot miss one.
    private final Map<Object, Runnable> pendingRefreshes = new LinkedHashMap<>();

    // Refreshes submitted and not yet applied, replaced by a later one or failed, by file; guarded by pendingRefreshes
    private final Map<Object, Set<Object>> submittedRefreshes = new HashMap<>();

    public LSPDiagnosticsApplier(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static LSPDiagnosticsApplier getInstance(@NotNull Project project) {
        return project.getService(LSPDiagnosticsApplier.class);
    }

    public void scheduleRefresh(@NotNull VirtualFile file,
                                @Nullable Document document) {
        scheduleRefresh(file, null, document);
    }

    public void scheduleRefresh(@NotNull PsiFile file,
                                @NotNull Document document) {
        scheduleRefresh(null, file, document);
    }

    /**
     * Schedule a reactive refresh of diagnostics for the given file.
     * Called when the server sends publishDiagnostics notification.
     */
    private void scheduleRefresh(@Nullable VirtualFile file,
                                 @Nullable PsiFile psiFile,
                                 @Nullable Document document) {
        if (project.isDisposed()) {
            return;
        }
        Object key = refreshKey(file, psiFile);
        if (GROUP_ID == -1) {
            synchronized (pendingRefreshes) {
                if (GROUP_ID == -1) {
                    // Nothing applies these diagnostics again unless they change, so keep the refresh
                    // until LSPDiagnosticsPassFactory registers the diagnostics group.
                    pendingRefreshes.put(key, () -> scheduleRefresh(file, psiFile, document));
                    return;
                }
            }
        }

        // Capture Document modification stamp as early as possible to detect changes
        Long modificationStamp = document != null ? document.getModificationStamp() : null;
        // Tracked before it is submitted, as the UI thread can apply it before submit() returns
        Object refresh = new Object();
        synchronized (pendingRefreshes) {
            submittedRefreshes.computeIfAbsent(key, ignored -> new HashSet<>()).add(refresh);
        }
        try {
            ReadAction
                    .nonBlocking(() -> collectDiagnostics(file, psiFile, document, modificationStamp))
                    .coalesceBy(this, file != null ? file : psiFile)
                    .finishOnUiThread(ModalityState.defaultModalityState(), highlights -> {
                        try {
                            if (highlights != null && !project.isDisposed()) {
                                applyHighlights(highlights);
                            }
                        } finally {
                            refreshEnded(key, refresh);
                        }
                    })
                    .submit(AppExecutorUtil.getAppExecutorService())
                    // A refresh that a later one of the same file replaces is cancelled, and is never applied
                    .onError(error -> refreshEnded(key, refresh));
        } catch (RuntimeException | Error e) {
            // Not submitted, as when the executor rejects it: the refresh never runs
            refreshEnded(key, refresh);
            throw e;
        }
    }

    /**
     * Returns true while a refresh of the diagnostics of the given file has yet to show them: one that waits for the
     * diagnostics pass to be registered, or one submitted and not yet applied. A refresh that a later one of the same
     * file replaces, or that fails, is never applied and no longer counts.
     *
     * @param file the file.
     * @return true while a refresh of the diagnostics of the given file is pending and false otherwise.
     */
    public boolean isRefreshPending(@NotNull VirtualFile file) {
        synchronized (pendingRefreshes) {
            return pendingRefreshes.containsKey(file) || submittedRefreshes.containsKey(file);
        }
    }

    private void refreshEnded(@NotNull Object key, @NotNull Object refresh) {
        synchronized (pendingRefreshes) {
            submittedRefreshes.computeIfPresent(key, (ignored, refreshes) -> {
                refreshes.remove(refresh);
                return refreshes.isEmpty() ? null : refreshes;
            });
        }
    }

    /**
     * Returns the key of the refreshes of a file: its virtual file, also for a refresh requested for its Psi file.
     */
    private static @NotNull Object refreshKey(@Nullable VirtualFile file, @Nullable PsiFile psiFile) {
        if (file != null) {
            return file;
        }
        VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
        return virtualFile != null ? virtualFile : psiFile;
    }

    /**
     * Runs the refreshes requested before {@link #GROUP_ID} was set.
     */
    void applyPendingRefreshes() {
        Map<Object, Runnable> refreshes;
        synchronized (pendingRefreshes) {
            refreshes = new LinkedHashMap<>(pendingRefreshes);
        }
        // A refresh stops waiting once it is submitted, so that isRefreshPending() is true all the way through
        refreshes.forEach((key, refresh) -> {
            try {
                refresh.run();
            } finally {
                synchronized (pendingRefreshes) {
                    pendingRefreshes.remove(key, refresh);
                }
            }
        });
    }

    private static class HighlightsToApply {
        final VirtualFile file;
        final Document document;
        final List<HighlightInfo> highlights;
        final int groupId;
        final long modificationStamp;

        HighlightsToApply(@NotNull VirtualFile file,
                          @NotNull Document document,
                          @NotNull List<HighlightInfo> highlights,
                          int groupId,
                          long modificationStamp) {
            this.file = file;
            this.document = document;
            this.highlights = highlights;
            this.groupId = groupId;
            this.modificationStamp = modificationStamp;
        }
    }

    @Nullable
    private HighlightsToApply collectDiagnostics(@Nullable VirtualFile file,
                                                 @Nullable PsiFile psiFile,
                                                 @Nullable Document document,
                                                 @Nullable Long modificationStamp) {
        if (project.isDisposed() || GROUP_ID == -1) {
            return null;
        }

        if (file != null) {
            psiFile = LSPIJUtils.getPsiFile(file, project);
            if (psiFile == null) {
                return null;
            }
            document = LSPIJUtils.getDocument(psiFile);
            if (document == null) {
                return null;
            }
            // Capture stamp here since it wasn't provided
            if (modificationStamp == null) {
                modificationStamp = document.getModificationStamp();
            }
        } else if (psiFile != null) {
            file = psiFile.getVirtualFile();
            if (file == null) {
                return null;
            }
        }

        // Collect diagnostics
        LSPDiagnosticsCollector collector = new LSPDiagnosticsCollector(psiFile);
        List<HighlightInfo> highlights = collector.collect();
        if (highlights == null) {
            highlights = List.of();
        }

        return new HighlightsToApply(file, document, highlights, GROUP_ID, modificationStamp);
    }

    @SuppressWarnings("deprecation")
    private void applyHighlights(@NotNull HighlightsToApply data) {
        // Check if document was modified since we collected diagnostics
        // If modified, reschedule so that diagnostics are eventually applied
        if (data.document.getModificationStamp() != data.modificationStamp) {
            scheduleRefresh(data.file, null, data.document);
            return;
        }

        UpdateHighlightersUtil.setHighlightersToEditor(
                project,
                data.document,
                0,
                data.document.getTextLength(),
                data.highlights,
                null,
                data.groupId
        );
        removeHighlightersOfOtherContexts(data);
    }

    /**
     * Removes the highlighters that the diagnostics applied last time for the document and that the highlights just
     * applied don't reuse. Since IntelliJ 2025.1, a highlighter belongs to the code insight context of its file, which is
     * the file's module, and {@link UpdateHighlightersUtil#setHighlightersToEditor} only replaces those of the file's
     * current context. So after a file moved to another module, the diagnostics shown before the move would stay.
     * Without such contexts, setHighlightersToEditor has already disposed or reused them all.
     */
    private void removeHighlightersOfOtherContexts(@NotNull HighlightsToApply data) {
        Set<HighlightInfo> applied = Collections.newSetFromMap(new IdentityHashMap<>());
        applied.addAll(data.highlights);
        var markup = DocumentMarkupModel.forDocument(data.document, project, true);
        var previous = markup.getUserData(APPLIED_HIGHLIGHTERS);
        if (previous != null) {
            for (var highlighter : previous) {
                // A reused highlighter refers to the HighlightInfo that was just applied
                var info = highlighter.isValid() ? HighlightInfo.fromRangeHighlighter(highlighter) : null;
                if (info != null && !applied.contains(info)) {
                    highlighter.dispose();
                }
            }
        }
        // setHighlightersToEditor gives each highlight the highlighter that it created or reused for it, so the other
        // highlighters of the document need no visit
        List<RangeHighlighter> current = new ArrayList<>();
        for (var info : data.highlights) {
            var highlighter = info.getHighlighter();
            if (highlighter != null) {
                current.add(highlighter);
            }
        }
        markup.putUserData(APPLIED_HIGHLIGHTERS, current.isEmpty() ? null : current);
    }

    @Override
    public void dispose() {
        // Nothing to clean up
    }
}

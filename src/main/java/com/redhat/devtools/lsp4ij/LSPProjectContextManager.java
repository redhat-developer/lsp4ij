package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory;
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener;
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class LSPProjectContextManager {

    private final Project project;
    private boolean ready;
    private final Set<VirtualFile> filesToRefresh;

    private LSPProjectContextManager(@NotNull Project project) {
        this.project = project;
        this.filesToRefresh = new CopyOnWriteArraySet<>();
        project.getMessageBus().connect().subscribe(ProjectIndexingActivityHistoryListener.Companion.getTOPIC(), new ProjectIndexingActivityHistoryListener() {
            @Override
            public void onFinishedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
               ready=true;
            }

            @Override
            public void onFinishedScanning(@NotNull ProjectScanningHistory history) {
                ready=true;
            }

            @Override
            public void onStartedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
                ready=false;
            }

            @Override
            public void onStartedScanning(@NotNull ProjectScanningHistory history) {
                ready=true;
            }
        });
    }

    /**
     * Returns the language server manager instance for the given project.
     *
     * @param project the project.
     * @return the language server manager instance for the given project.
     */
    public static LSPProjectContextManager getInstance(@NotNull Project project) {
        return project.getService(LSPProjectContextManager.class);
    }

    public static boolean isReady(@Nullable Project project) {
        return project != null && !project.isDisposed() && getInstance(project).isReady();
    }

    public boolean isReady() {
        return ready;
    }

    //public void setReady(boolean ready) {
      //  this.ready = ready;
    //}

    public void refreshProjectEditors() {
        if (filesToRefresh.isEmpty()) {
            return;
        }
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            doRefreshProjectEditors();
        } else {
            ReadAction.run(() -> doRefreshProjectEditors());
        }
        filesToRefresh.clear();
    }

    private void doRefreshProjectEditors() {
        for(VirtualFile file : filesToRefresh) {
            PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
        }
    }

    public boolean addFileToRefreshIfNotReady(VirtualFile file) {
        if (ready) {
            return false;
        }
        filesToRefresh.add(file);
        return true;
    }
}

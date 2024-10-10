package com.redhat.devtools.lsp4ij.client;

import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory;
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener;
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory;
import org.jetbrains.annotations.NotNull;

public class ProjectIndexingListener implements ProjectIndexingActivityHistoryListener {

    @Override
    public void onStartedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).dumbIndexing = true;
    }

    @Override
    public void onFinishedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).dumbIndexing = false;
    }

    @Override
    public void onStartedScanning(@NotNull ProjectScanningHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).scanning = true;
    }

    @Override
    public void onFinishedScanning(@NotNull ProjectScanningHistory history) {
        ProjectIndexingManager.getInstance(history.getProject()).scanning = false;
    }
}

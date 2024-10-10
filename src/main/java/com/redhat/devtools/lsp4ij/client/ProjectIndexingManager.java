package com.redhat.devtools.lsp4ij.client;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory;
import com.intellij.util.indexing.diagnostic.ProjectIndexingActivityHistoryListener;
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ProjectIndexingManager implements Disposable {

    private final MessageBusConnection connection;

    private boolean dumbIndexing;
    private boolean scanning;

    private ProjectIndexingManager(@NotNull Project project) {
        connection = project.getMessageBus().connect();
        connection.subscribe(ProjectIndexingActivityHistoryListener.Companion.getTOPIC(), new ProjectIndexingActivityHistoryListener() {

            @Override
            public void onStartedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
                dumbIndexing = true;
            }

            @Override
            public void onFinishedDumbIndexing(@NotNull ProjectDumbIndexingHistory history) {
                dumbIndexing = false;
            }

            @Override
            public void onStartedScanning(@NotNull ProjectScanningHistory history) {
                scanning = true;
            }

            @Override
            public void onFinishedScanning(@NotNull ProjectScanningHistory history) {
                scanning = false;
            }

        });
    }

    public static ProjectIndexingManager getInstance(@NotNull Project project) {
        return project.getService(ProjectIndexingManager.class);
    }

    @Override
    public void dispose() {
        connection.dispose();
    }

    public CompletableFuture<Void> waitForIndexing() {
        return CompletableFuture.runAsync(() -> {
            while (scanning && dumbIndexing) {

            }
        });
    }
}

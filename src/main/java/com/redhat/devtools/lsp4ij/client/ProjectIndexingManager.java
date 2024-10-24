package com.redhat.devtools.lsp4ij.client;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ProjectIndexingManager implements Disposable {

    private static final Key<RefreshFeatures> FEATURES_TO_REFRESH = Key.create("lsp.features.to.refresh.after.indexing");

    private record RefreshFeatures(Set<String> features, CompletableFuture<?> indexingFinished) {}

    private final @NotNull Project project;
    boolean dumbIndexing;
    boolean scanning;

    ProjectIndexingManager(@NotNull Project project) {
        this.project = project;
    }

    public static ProjectIndexingManager getInstance(@NotNull Project project) {
        return project.getService(ProjectIndexingManager.class);
    }

    public static void refreshFeatureWhenIndexingFinished(@NotNull PsiFile file, String lspFeature) {
        var refreshFeatures = file.getUserData(FEATURES_TO_REFRESH);
        if (refreshFeatures == null) {
            final Set<String> features = new HashSet<>();
            var indexingFinished = waitForIndexing(file.getProject())
                    .thenApply(unused -> {
                        System.err.println(features);
                        DaemonCodeAnalyzer.getInstance(file.getProject()).restart(file);
                        file.putUserData(FEATURES_TO_REFRESH, null);
                        return null;
                    });
            refreshFeatures = new RefreshFeatures(features, indexingFinished);
            file.putUserData(FEATURES_TO_REFRESH, refreshFeatures);
        }
        refreshFeatures.features().add(lspFeature);
    }

    @Override
    public void dispose() {
    }

    public boolean isIndexing() {
        return scanning || dumbIndexing || DumbService.isDumb(project);
    }

    public CompletableFuture<Void> waitForIndexing() {
        return CompletableFutures.computeAsync(cancelChecker -> {
            while (isIndexing()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cancelChecker.checkCanceled();
            }
            return null;
        });
    }

    public static boolean isIndexing(@NotNull Project project) {
        return getInstance(project).isIndexing();
    }

    public static CompletableFuture<Void> waitForIndexing(@NotNull Project project) {
        return getInstance(project).waitForIndexing();
    }

}

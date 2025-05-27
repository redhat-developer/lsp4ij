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
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Singleton manager responsible for providing and caching {@link GitHubAssetFetcher} instances.
 *
 * <p>This class ensures that a unique {@link GitHubAssetFetcher} is created per GitHub repository
 * (identified by owner and repository name) and reused thereafter.</p>
 */
public class GitHubAssetFetcherManager {

    private final @NotNull Map<String, GitHubAssetFetcher> assetFetchers;

    /**
     * Returns the singleton instance of {@code GitHubAssetFetcherManager}.
     *
     * <p>Obtains the instance from the IntelliJ application service infrastructure.</p>
     *
     * @return the singleton {@code GitHubAssetFetcherManager} instance
     */
    public static @NotNull GitHubAssetFetcherManager getInstance() {
        return ApplicationManager.getApplication().getService(GitHubAssetFetcherManager.class);
    }

    /**
     * Private constructor to enforce singleton pattern.
     * Initializes the internal cache for asset fetchers.
     */
    private GitHubAssetFetcherManager() {
        this.assetFetchers = new HashMap<>();
    }

    /**
     * Returns a {@link GitHubAssetFetcher} for the specified GitHub repository.
     *
     * <p>If a fetcher for the given owner and repository already exists, it is returned.
     * Otherwise, a new fetcher is created, cached, and returned.</p>
     *
     * @param owner the GitHub repository owner (username or organization)
     * @param repository the GitHub repository name
     * @return a cached or newly created {@link GitHubAssetFetcher} for the specified repository
     */
    public GitHubAssetFetcher getAssetFetcher(String owner, String repository) {
        String key = owner + "#" + repository;
        return assetFetchers.computeIfAbsent(key, k -> new GitHubAssetFetcher(owner, repository));
    }
}

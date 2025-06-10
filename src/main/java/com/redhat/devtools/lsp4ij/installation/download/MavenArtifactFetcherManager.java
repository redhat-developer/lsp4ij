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
 * Singleton manager for providing and caching {@link MavenArtifactFetcher} instances.
 */
public class MavenArtifactFetcherManager {

    private final @NotNull Map<String, MavenArtifactFetcher> artifactFetchers = new HashMap<>();

    public static @NotNull MavenArtifactFetcherManager getInstance() {
        return ApplicationManager.getApplication().getService(MavenArtifactFetcherManager.class);
    }

    public MavenArtifactFetcher getArtifactFetcher(@NotNull String groupId, @NotNull String artifactId) {
        String key = groupId + "#" + artifactId;
        return artifactFetchers.computeIfAbsent(key, k -> new MavenArtifactFetcher(groupId, artifactId));
    }
}

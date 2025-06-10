/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryBase;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcher;
import com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherManager;
import com.redhat.devtools.lsp4ij.installation.download.MavenArtifactFetcherManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <pre>
 * "download": {
 *     "name": "Download rust-analyzer",*
 *     "url": {
 *       "windows": "",
 *       "default": ""
 *     },
 *     "onFail": { ...
 *     }
 * }
 * </pre>
 *
 */
public class DownloadTaskFactory extends InstallerTaskFactoryBase {

    private static final String URL_JSON_PROPERTY = "url";

    private static final String GITHUB_JSON_PROPERTY = "github";
    private static final String GITHUB_OWNER_JSON_PROPERTY = "owner";
    private static final String GITHUB_REPOSITORY_JSON_PROPERTY = "repository";
    private static final String GITHUB_ASSET_JSON_PROPERTY = "asset";
    private static final String GITHUB_PRERELEASE_JSON_PROPERTY = "prerelease";

    private static final String MAVEN_JSON_PROPERTY = "maven";
    private static final String MAVEN_GROUP_ID_JSON_PROPERTY = "groupId";
    private static final String MAVEN_ARTIFACT_ID_JSON_PROPERTY = "artifactId";

    private static final String OUTPUT_JSON_PROPERTY = "output";
    private static final String OUTPUT_DIR_JSON_PROPERTY = "dir";
    private static final String OUTPUT_FILE_JSON_PROPERTY = "file";
    private static final String OUTPUT_FILE_NAME_JSON_PROPERTY = "name";
    private static final String OUTPUT_FILE_EXECUTABLE_JSON_PROPERTY = "executable";

    @Override
    protected @NotNull InstallerTask create(@Nullable String id,
                                            @Nullable String name,
                                            @Nullable InstallerTask onFail,
                                            @Nullable InstallerTask onSuccess,
                                            @NotNull JsonObject json,
                                            @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return new DownloadTask(id, name, onFail, onSuccess, getDownloadUrl(json), getAssetFetcher(json), getOutputInfo(json), serverInstallerDescriptor);
    }

    private static @Nullable String getDownloadUrl(@NotNull JsonObject json) {
        return getStringFromOs(json, URL_JSON_PROPERTY);
    }

    private static @Nullable DownloadTask.AssetFetcherInfo getAssetFetcher(@NotNull JsonObject json) {
        DownloadTask.AssetFetcherInfo assetFetcher = getGithubAssetFetcher(json);
        if (assetFetcher != null) {
            return assetFetcher;
        }
        return getMavenArtifactFetcher(json);
    }

    private static DownloadTask.@Nullable AssetFetcherInfo getGithubAssetFetcher(@NotNull JsonObject json) {
        if (!json.has(GITHUB_JSON_PROPERTY)) {
            return null;
        }
        var githubElement = json.get(GITHUB_JSON_PROPERTY);
        if (!githubElement.isJsonObject()) {
            return null;
        }
        var githubObj = githubElement.getAsJsonObject();
        if (!githubObj.has(GITHUB_OWNER_JSON_PROPERTY) || !githubObj.has(GITHUB_REPOSITORY_JSON_PROPERTY)) {
            return null;
        }
        String owner = JSONUtils.getString(githubObj, GITHUB_OWNER_JSON_PROPERTY);
        String repository = JSONUtils.getString(githubObj, GITHUB_REPOSITORY_JSON_PROPERTY);
        String assertPattern = getAssertPattern(githubObj);
        if (assertPattern == null) {
            return null;
        }
        boolean prerelease = JSONUtils.getBoolean(githubObj, GITHUB_PRERELEASE_JSON_PROPERTY);
        var assetFetcher = GitHubAssetFetcherManager.getInstance().getAssetFetcher(owner, repository);
        return new DownloadTask.AssetFetcherInfo(assetFetcher,
                new GitHubAssetFetcher.ReleaseMatcher(prerelease),
                new GitHubAssetFetcher.AssetMatcher(assertPattern));
    }

    private static DownloadTask.@Nullable AssetFetcherInfo getMavenArtifactFetcher(@NotNull JsonObject json) {
        if (!json.has(MAVEN_JSON_PROPERTY)) {
            return null;
        }
        var mavenElement = json.get(MAVEN_JSON_PROPERTY);
        if (!mavenElement.isJsonObject()) {
            return null;
        }
        var mavenObj = mavenElement.getAsJsonObject();
        if (!mavenObj.has(MAVEN_GROUP_ID_JSON_PROPERTY) || !mavenObj.has(MAVEN_ARTIFACT_ID_JSON_PROPERTY)) {
            return null;
        }
        String groupId = JSONUtils.getString(mavenObj, MAVEN_GROUP_ID_JSON_PROPERTY);
        String artifactId = JSONUtils.getString(mavenObj, MAVEN_ARTIFACT_ID_JSON_PROPERTY);
        var assetFetcher = MavenArtifactFetcherManager.getInstance().getArtifactFetcher(groupId, artifactId);
        return new DownloadTask.AssetFetcherInfo(assetFetcher,
                obj  -> true,
                obj  -> true);

    }

    private static @Nullable String getAssertPattern(JsonObject githubObj) {
        return getStringFromOs(githubObj, GITHUB_ASSET_JSON_PROPERTY);
    }

    private DownloadTask.@Nullable OutputInfo getOutputInfo(@NotNull JsonObject json) {
        var outputObj = JSONUtils.getJsonObject(json, OUTPUT_JSON_PROPERTY);
        if (outputObj == null) {
            return null;
        }
        String dir = getStringFromOs(outputObj, OUTPUT_DIR_JSON_PROPERTY);

        // file
        String fileName = null;
        boolean executable = false;
        var fileObj = JSONUtils.getJsonObject(outputObj, OUTPUT_FILE_JSON_PROPERTY);
        if (fileObj != null) {
            fileName = getStringFromOs(fileObj, OUTPUT_FILE_NAME_JSON_PROPERTY);
            executable = JSONUtils.getBoolean(fileObj, OUTPUT_FILE_EXECUTABLE_JSON_PROPERTY);
        }
        return new DownloadTask.OutputInfo(dir, fileName, executable);
    }

}

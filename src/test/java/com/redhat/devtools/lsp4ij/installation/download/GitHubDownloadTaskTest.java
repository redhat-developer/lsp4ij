/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.DownloadTask;
import com.redhat.devtools.lsp4ij.installation.download.LocalHttpServer.Request;
import com.redhat.devtools.lsp4ij.installation.download.LocalHttpServer.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.RELEASES_PATH;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.TOKEN;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.releases;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A download task with a {@code github} source sends the GitHub token of the environment to the GitHub API, and not
 * with the download of the asset, and its console never shows the token (#2142).
 */
public class GitHubDownloadTaskTest {

    private static final String ASSET_PATH = "/download/v1.0/server.jar";

    private LocalHttpServer server;
    private Path outputDir;

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Before
    public void startServer() throws Exception {
        server = new LocalHttpServer();
        outputDir = Files.createTempDirectory("lsp4ij-github-download");
    }

    @After
    public void stopServer() throws Exception {
        server.close();
        NioFiles.deleteRecursively(outputDir);
    }

    @Test
    public void testTheDownloadOfTheAssetDoesNotGetTheToken() throws Exception {
        // The asset is on the host of the API, which GitHub's aren't, so that only the request decides
        server.answer(RELEASES_PATH, Response.ok(releases(server.url() + ASSET_PATH)));
        server.answer(ASSET_PATH, Response.ok("server"));

        var context = new RecordingContext();
        boolean installed = createTask(null).run(context);

        assertTrue(installed, context.output());
        assertEquals("server", Files.readString(outputDir.resolve("server.jar")));
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(ASSET_PATH, null)),
                server.requests(), "only the request to the GitHub API must have the token");
        assertNoToken(context);
    }

    @Test
    public void testTheConsoleDoesNotShowTheTokenWhenGitHubRefusesIt() throws Exception {
        server.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"));

        var context = new RecordingContext();
        boolean installed = createTask(null).run(context);

        assertFalse(installed, "the download has no url to fall back to");
        assertTrue(context.output().contains("Error while loading GitHub releases: "), context.output());
        assertNoToken(context);
    }

    /**
     * Returns a download task which downloads the asset {@code server.jar} of the latest release of
     * {@code owner/repo}, which it finds through the local GitHub API with the token
     * {@link GitHubAssetFetcherTest#TOKEN} in {@code GITHUB_TOKEN}, or else the given URL, into the output directory.
     */
    private @NotNull DownloadTask createTask(@Nullable String url) {
        var fetcher = new GitHubAssetFetcher("owner", "repo", server.url(), Map.of("GITHUB_TOKEN", TOKEN)::get);
        var assetFetcherInfo = new DownloadTask.AssetFetcherInfo(fetcher,
                GitHubAssetFetcher.RELEASE_MATCHER,
                new GitHubAssetFetcher.AssetMatcher("server.jar"));
        var outputInfo = new DownloadTask.OutputInfo(outputDir.toString(), null, false, false);
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        return new DownloadTask(null, "download", null, null, url, assetFetcherInfo, outputInfo, descriptor);
    }

    private static void assertNoToken(@NotNull RecordingContext context) {
        assertFalse(context.output().contains(TOKEN), "the console must not show the token");
        context.getStatus().forEach(status -> assertFalse(status.message().contains(TOKEN),
                "the notification must not show the token"));
    }

    /**
     * Records what an installer prints in its console.
     */
    static class RecordingContext extends InstallerContext {

        private final List<String> output = new CopyOnWriteArrayList<>();

        RecordingContext() {
            super(null, InstallerAction.RUN);
        }

        @Override
        public void print(@Nullable String message, @NotNull ConsoleViewContentType contentType) {
            if (message != null) {
                output.add(message);
            }
            super.print(message, contentType);
        }

        @NotNull String output() {
            return String.join("\n", output);
        }
    }
}

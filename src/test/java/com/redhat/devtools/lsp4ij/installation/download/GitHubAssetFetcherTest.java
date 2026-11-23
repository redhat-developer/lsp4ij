/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.redhat.devtools.lsp4ij.installation.download.LocalHttpServer.Request;
import com.redhat.devtools.lsp4ij.installation.download.LocalHttpServer.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookup of a GitHub release asks the GitHub API for the most releases it gives on one page, 100 instead of 30,
 * so that the latest release of a repository with many prereleases is among them (#1961). It sends the GitHub token of
 * the environment variable {@code GITHUB_TOKEN}, or else {@code GH_TOKEN}, to the GitHub API only, and never shows it
 * (#2142).
 */
public class GitHubAssetFetcherTest {

    static final String TOKEN = "ghp_test0123456789abcdefTOKEN";
    static final String OTHER_TOKEN = "github_pat_test0123456789OTHER";

    static final String RELEASES_PATH = "/repos/owner/repo/releases";

    private LocalHttpServer api;

    @BeforeEach
    void startApi() throws IOException {
        api = new LocalHttpServer();
    }

    @AfterEach
    void stopApi() {
        api.close();
    }

    @Test
    void theReleasesAreAskedForOneHundredAtATime() {
        assertEquals("https://api.github.com/repos/Open-MBEE/OpenSysML/releases?per_page=100",
                GitHubAssetFetcher.releasesUrl("Open-MBEE", "OpenSysML"));
    }

    @Test
    void theTokenOfGitHubTokenIsSentToTheGitHubApi() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
        assertTrue(reporter.output().contains("(with the token of the environment variable GITHUB_TOKEN)"),
                "the console must say which variable the token comes from: " + reporter.output());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void theTokenOfGhTokenIsSentToTheGitHubApi() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of("GH_TOKEN", TOKEN), reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
        assertTrue(reporter.output().contains("(with the token of the environment variable GH_TOKEN)"),
                reporter.output());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void gitHubTokenTakesPrecedenceOverGhToken() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of("GITHUB_TOKEN", TOKEN, "GH_TOKEN", OTHER_TOKEN), reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
        assertNoToken(reporter, TOKEN);
        assertNoToken(reporter, OTHER_TOKEN);
    }

    @Test
    void noTokenIsSentWithoutTheVariables() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of(), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, null)), api.requests());
        // As before
        assertEquals("> Loading GitHub releases: " + api.url() + RELEASES_PATH + "?per_page=100",
                reporter.lines().get(0));
    }

    @Test
    void noTokenIsSentWhenTheVariablesAreBlank() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of("GITHUB_TOKEN", " ", "GH_TOKEN", ""), reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, null)), api.requests());
        assertFalse(reporter.output().contains("token"), reporter.output());
    }

    @Test
    void aBlankGitHubTokenLeavesItToGhToken() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of("GITHUB_TOKEN", "\t", "GH_TOKEN", TOKEN), reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
    }

    @Test
    void theSpacesAroundTheTokenAreNotSent() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        fetch(Map.of("GITHUB_TOKEN", " " + TOKEN + "\n"), new RecordingReporter());

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
    }

    @Test
    void aTokenWithALineBreakInsideIsNotSentNorShown() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));
        String token = TOKEN + "\nX-Injected: " + OTHER_TOKEN;

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", token), reporter);

        // HttpURLConnection would throw an exception whose message shows the header
        assertNoToken(reporter, TOKEN);
        assertNoToken(reporter, OTHER_TOKEN);
        assertTrue(reporter.output().contains(
                        "The environment variable GITHUB_TOKEN has characters which a GitHub token doesn't have"),
                reporter.output());
        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, null)), api.requests());
    }

    @Test
    void theTokenGoesAlongARedirectWithinTheGitHubApi() {
        // As for a repository which was renamed
        api.answer(RELEASES_PATH, Response.redirect(301, api.url() + "/repositories/42/releases?per_page=100"));
        api.answer("/repositories/42/releases", Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN),
                        new Request("/repositories/42/releases", "Bearer " + TOKEN)),
                api.requests());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void theTokenDoesNotGoAlongARedirectToAnotherHost() throws IOException {
        try (var other = new LocalHttpServer()) {
            api.answer(RELEASES_PATH, Response.redirect(302, other.url() + "/releases"));
            other.answer("/releases", Response.ok(releases(other.url() + "/download/server.jar")));

            var reporter = new RecordingReporter();
            String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

            assertEquals(other.url() + "/download/server.jar", downloadUrl);
            assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
            assertEquals(List.of(new Request("/releases", null)), other.requests(),
                    "a redirect to another host must not get the token");
            assertNoToken(reporter, TOKEN);
        }
    }

    @Test
    void theTokenIsNotShownWhenTheRequestFails() {
        for (int code : List.of(401, 403, 404, 500)) {
            api.answer(RELEASES_PATH, new Response(code, Map.of(), "{\"message\":\"failed\"}"));

            var reporter = new RecordingReporter();
            assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

            assertTrue(reporter.output().contains("Error while loading GitHub releases: "), reporter.output());
            assertNoToken(reporter, TOKEN);
        }
    }

    /**
     * Returns the download URL of the asset {@code server.jar} of the latest release of {@code owner/repo}, which a new
     * fetcher finds through the local GitHub API, with the given environment variables.
     */
    private @Nullable String fetch(@NotNull Map<String, String> environment, @NotNull Reporter reporter) {
        return new GitHubAssetFetcher("owner", "repo", api.url(), environment::get)
                .getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), reporter);
    }

    /**
     * Returns the JSON of the releases of the GitHub API, with one release, whose one asset {@code server.jar} has the
     * given download URL.
     */
    static @NotNull String releases(@NotNull String downloadUrl) {
        return """
                [{"tag_name": "v1.0", "prerelease": false,
                  "assets": [{"name": "server.jar", "browser_download_url": "%s"}]}]""".formatted(downloadUrl);
    }

    static void assertNoToken(@NotNull RecordingReporter reporter, @NotNull String token) {
        assertFalse(reporter.output().contains(token), "the console must not show the token");
    }

    /**
     * Records what a fetcher reports, as the console of the installer shows it: an exception with its stack trace.
     */
    static class RecordingReporter implements Reporter {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void setText(@NotNull String text) {
            lines.add(text);
        }

        @Override
        public void setText(@NotNull String text, @NotNull Exception e) {
            StringWriter stackTrace = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTrace));
            lines.add(text + stackTrace);
        }

        @Override
        public void checkCanceled() {
        }

        @NotNull List<String> lines() {
            return lines;
        }

        @NotNull String output() {
            return String.join("\n", lines);
        }
    }
}

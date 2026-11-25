/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.redhat.devtools.lsp4ij.installation.download.GitHubAccountTokenProvider.AccountToken;
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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookup of a GitHub release asks the GitHub API for the most releases it gives on one page, 100 instead of 30,
 * so that the latest release of a repository with many prereleases is among them (#1961). It sends the GitHub token of
 * the environment variable {@code GITHUB_TOKEN}, or else {@code GH_TOKEN} (#2142), or else of the LSP4IJ settings, or
 * else of the IDE's GitHub account on the server of the GitHub API (#2188), to the GitHub API only, and never shows it.
 */
public class GitHubAssetFetcherTest {

    static final String TOKEN = "ghp_test0123456789abcdefTOKEN";
    static final String OTHER_TOKEN = "github_pat_test0123456789OTHER";
    // As the OAuth token of the IDE's GitHub account
    static final String ACCOUNT_TOKEN = "gho_test0123456789abcdefACCOUNT";

    // What the console says of a token of the LSP4IJ settings which GitHub refuses, after "(HTTP 401)"
    static final String SETTINGS_RENEWAL = ": the token is invalid or has expired. Set a valid token in Settings | "
            + "Languages & Frameworks | Language Servers (LSP4IJ), or remove it.";

    static final String RELEASES_PATH = "/repos/owner/repo/releases";

    // 2026-11-23T14:35:07Z, whose seconds aren't 0, which LocalTime#toString would leave out
    static final long RESET = 1795444507L;

    // What the console adds to the message of a token which GitHub refuses
    static final String RETRY = " Trying again without the token.";

    // What the message of the rate limit without a token says of a token, before what it raises the limit to
    static final String RAISE_LIMIT = " A GitHub token in Settings | Languages & Frameworks | Language Servers "
            + "(LSP4IJ), or in the environment variable GITHUB_TOKEN of the IDE, raises the";

    // As in GitHub's documentation: the authorization request has the id of the token, not the token
    static final String SSO_URL = "https://github.com/orgs/owner/sso?authorization_request="
            + "AZSCKtL4U8yX1H3sCQIVnVgmjmon5fWxks5YrqhJgah0b2tlbl9pZM4EuMz4";

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
        for (var response : List.of(error(401), error(403), error(404), error(500),
                rateLimit(403, 5000, RESET), rateLimit(429, 5000, RESET),
                new Response(403, Map.of("Retry-After", "60"), ""),
                new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL), ""),
                new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL + "&t=" + TOKEN), ""),
                new Response(403, Map.of("X-GitHub-SSO", "partial-results; organizations=21955855"), ""),
                new Response(403, Map.of("X-Accepted-GitHub-Permissions", "contents=read"), ""),
                new Response(404, Map.of("X-Accepted-GitHub-Permissions", "contents=read"), ""))) {
            api.answer(RELEASES_PATH, response);

            var reporter = new RecordingReporter();
            assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

            assertTrue(reporter.output().contains("Error while loading GitHub releases: "), reporter.output());
            assertNoToken(reporter, TOKEN);
        }
    }

    @Test
    void theRateLimitSaysWhenItResetsAndThatATokenRaisesIt() {
        for (int code : List.of(403, 429)) {
            api.answer(RELEASES_PATH, rateLimit(code, 60, RESET));

            var reporter = new RecordingReporter();
            assertNull(fetch(Map.of(), reporter));

            assertEquals("Error while loading GitHub releases: GitHub API rate limit reached: 60 requests an hour "
                            + "(HTTP " + code + "). It resets at " + localTime(RESET) + " (local time)." + RAISE_LIMIT
                            + " limit to 5,000 requests an hour.",
                    reporter.lines().get(1).strip(), "the message must explain the error, without a stack trace");
        }
    }

    @Test
    void theRateLimitResetsAtTheLocalTime() {
        api.answer(RELEASES_PATH, rateLimit(403, 60, RESET));
        TimeZone timeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            var reporter = new RecordingReporter();
            fetch(Map.of(), reporter);

            // 14 hours ahead of UTC
            String local = LocalTime.ofInstant(Instant.ofEpochSecond(RESET), ZoneId.of("Pacific/Kiritimati"))
                    .format(DateTimeFormatter.ISO_LOCAL_TIME);
            assertTrue(reporter.output().contains("It resets at " + local + " (local time)."), reporter.output());
        } finally {
            TimeZone.setDefault(timeZone);
        }
    }

    @Test
    void theRateLimitOfATokenNamesItsVariable() {
        api.answer(RELEASES_PATH, rateLimit(403, 5000, RESET));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GH_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API rate limit reached for the token of the "
                        + "environment variable GH_TOKEN: 5000 requests an hour (HTTP 403). It resets at "
                        + localTime(RESET) + " (local time)." + RETRY,
                reporter.lines().get(1).strip());
    }

    @Test
    void aSecondaryRateLimitSaysWhenToTryAgain() {
        api.answer(RELEASES_PATH, new Response(403, Map.of("Retry-After", "60", "X-RateLimit-Remaining", "42"), ""));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of(), reporter));

        String message = reporter.lines().get(1).strip();
        assertTrue(message.startsWith("Error while loading GitHub releases: GitHub API rate limit reached (HTTP 403). "
                + "Try again in 60 seconds, at "), message);
        assertTrue(message.endsWith(" (local time)." + RAISE_LIMIT + " limit."), message);
    }

    @Test
    void aTokenThatGitHubRefusesIsSaidToBeRefused() {
        api.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GH_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GH_TOKEN (HTTP 401): the token is invalid or has expired. Set GH_TOKEN to a valid token, or "
                        + "unset it." + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void otherErrorsArePrintedAsBefore() {
        // A 401 and a 403 without a token, also with the permissions of a token, and a 403 which isn't the rate limit
        for (var response : List.of(error(401), error(403), error(500),
                new Response(403, Map.of("X-Accepted-GitHub-Permissions", "contents=read"), ""),
                new Response(403, Map.of("X-RateLimit-Remaining", "42", "X-RateLimit-Reset", "" + RESET), ""))) {
            api.answer(RELEASES_PATH, response);

            var reporter = new RecordingReporter();
            assertNull(fetch(Map.of(), reporter));

            assertTrue(reporter.lines().get(1).startsWith("Error while loading GitHub releases: "
                            + "java.lang.RuntimeException: GitHub API returned HTTP " + response.code()
                            + System.lineSeparator() + "\tat "),
                    reporter.output());
        }
    }

    @Test
    void aTokenWhichSingleSignOnDoesNotAuthorizeIsSaidToNeedItsLink() {
        api.answer(RELEASES_PATH, new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL),
                "{\"message\": \"Resource protected by organization SAML enforcement.\"}"));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GITHUB_TOKEN (HTTP 403): the organization owner enforces SAML single sign-on, and the token "
                        + "isn't authorized for it. Authorize it at " + SSO_URL
                        + ", a link which expires after an hour." + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aTokenWhichSingleSignOnDoesNotAuthorizeForPartialResultsIsSaidToNeedIt() {
        api.answer(RELEASES_PATH, new Response(403,
                Map.of("X-GitHub-SSO", "partial-results; organizations=21955855,20582480"), ""));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GH_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GH_TOKEN (HTTP 403): the organization owner enforces SAML single sign-on, and the token "
                        + "isn't authorized for it. Authorize it for owner in the settings of the token on GitHub."
                        + RETRY,
                reporter.lines().get(1).strip());
    }

    @Test
    void aSingleSignOnLinkWithTheTokenIsNotShown() {
        api.answer(RELEASES_PATH, new Response(403,
                Map.of("X-GitHub-SSO", "required; url=" + SSO_URL + "&t=" + TOKEN), ""));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertNoToken(reporter, TOKEN);
        assertTrue(reporter.lines().get(1).strip().endsWith(
                "Authorize it for owner in the settings of the token on GitHub." + RETRY), reporter.output());
    }

    @Test
    void aTokenWhichLacksAPermissionIsSaidToLackIt() {
        api.answer(RELEASES_PATH, new Response(403, Map.of("X-Accepted-GitHub-Permissions", "contents=read"),
                "{\"message\": \"Resource not accessible by personal access token\"}"));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GITHUB_TOKEN (HTTP 403): the token lacks the permission contents=read." + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aTokenWhichLacksPermissionsIsSaidToLackEitherSet() {
        api.answer(RELEASES_PATH, new Response(403, Map.of("X-Accepted-GitHub-Permissions",
                "pull_requests=read,contents=read; issues=read,contents=read"), ""));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GITHUB_TOKEN (HTTP 403): the token lacks the permissions pull_requests=read and "
                        + "contents=read, or issues=read and contents=read." + RETRY,
                reporter.lines().get(1).strip());
    }

    @Test
    void aRepositoryNotFoundWithATokenIsSaidToBeMissingOrPrivate() {
        api.answer(RELEASES_PATH, error(404));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API returned HTTP 404: the repository owner/repo "
                        + "was not found, or it is private and the token of the environment variable GITHUB_TOKEN "
                        + "can't read it." + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aRepositoryNotFoundWithATokenNamesThePermissionWhichReadsIt() {
        api.answer(RELEASES_PATH, new Response(404, Map.of("X-Accepted-GitHub-Permissions", "contents=read"), ""));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API returned HTTP 404: the repository owner/repo "
                        + "was not found, or it is private and the token of the environment variable GITHUB_TOKEN "
                        + "can't read it. Reading it needs the permission contents=read." + RETRY,
                reporter.lines().get(1).strip());
    }

    @Test
    void aRepositoryNotFoundWithoutATokenIsSaidToBeMissingOrPrivate() {
        api.answer(RELEASES_PATH, error(404));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of(), reporter));

        assertEquals("Error while loading GitHub releases: GitHub API returned HTTP 404: the repository owner/repo "
                        + "was not found, or it is private.",
                reporter.lines().get(1).strip());
    }

    @Test
    void aTokenThatGitHubRefusesIsNotNeededForAPublicRepository() {
        api.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null)),
                api.requests(), "the request must be tried again once, without the token");
        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GITHUB_TOKEN (HTTP 401): the token is invalid or has expired. Set GITHUB_TOKEN to a valid "
                        + "token, or unset it." + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aTokenWhichSingleSignOnDoesNotAuthorizeIsNotNeededForAPublicRepository() {
        api.answer(RELEASES_PATH, new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL), ""),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null)),
                api.requests());
        assertTrue(reporter.lines().get(1).contains("enforces SAML single sign-on"), reporter.output());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aTokenWhichLacksAPermissionIsNotNeededForAPublicRepository() {
        api.answer(RELEASES_PATH, new Response(403, Map.of(),
                        "{\"message\": \"Resource not accessible by personal access token\"}"),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(2, api.requests().size(), api.requests().toString());
        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the environment variable "
                        + "GITHUB_TOKEN (HTTP 403)." + RETRY,
                reporter.lines().get(1).strip());
    }

    @Test
    void theRateLimitOfATokenLeavesTheRequestToTheLimitWithoutIt() {
        api.answer(RELEASES_PATH, rateLimit(403, 5000, RESET),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null)),
                api.requests());
    }

    @Test
    void aRepositoryNotFoundWithATokenIsAskedForOnceMoreWithoutIt() {
        api.answer(RELEASES_PATH, error(404));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null)),
                api.requests(), "the request must be tried again once only");
        assertEquals("Error while loading GitHub releases: GitHub API returned HTTP 404: the repository owner/repo "
                        + "was not found, or it is private.",
                reporter.lines().get(2).strip(), "the message must be the one of the request without the token");
        assertEquals(3, reporter.lines().size(), reporter.output());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aRequestWithoutATokenIsNotTriedAgain() {
        for (var response : List.of(error(401), error(403), error(404), error(500), rateLimit(403, 60, RESET),
                new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL), ""))) {
            try (var server = new LocalHttpServer()) {
                server.answer(RELEASES_PATH, response, Response.ok(releases(server.url() + "/download/server.jar")));

                var reporter = new RecordingReporter();
                assertNull(new GitHubAssetFetcher("owner", "repo", server.url(), name -> null)
                        .getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), reporter));

                assertEquals(List.of(new Request(RELEASES_PATH, null)), server.requests(),
                        "HTTP " + response.code() + " without a token must not be tried again");
                assertFalse(reporter.output().contains(RETRY), reporter.output());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void aSecondaryRateLimitOfATokenIsNotTriedAgain() {
        // GitHub asks to wait for the seconds of Retry-After before the next request
        api.answer(RELEASES_PATH, new Response(403, Map.of("Retry-After", "60", "X-RateLimit-Remaining", "4000"), ""),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        assertNull(fetch(Map.of("GITHUB_TOKEN", TOKEN), reporter));

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
    }

    @Test
    void theLogHasEachRequestToTheGitHubApiAtDebugLevel() {
        api.answer(RELEASES_PATH, new Response(200,
                Map.of("X-RateLimit-Limit", "5000", "X-RateLimit-Remaining", "4999"),
                releases(api.url() + "/download/server.jar")));

        try (var log = new LogRecorder()) {
            fetch(Map.of("GITHUB_TOKEN", TOKEN), new RecordingReporter());

            assertEquals(1, log.lines().size(), log.output());
            assertTrue(log.lines().get(0).matches(Pattern.quote("FINE GitHub API request " + api.url() + RELEASES_PATH
                            + "?per_page=100 with the token of the environment variable GITHUB_TOKEN: HTTP 200 in ")
                            + "\\d+" + Pattern.quote(" ms, X-RateLimit-Remaining 4999 of X-RateLimit-Limit 5000")),
                    log.output());
            assertNoToken(log, TOKEN);
        }
    }

    @Test
    void theLogWarnsOfATokenThatGitHubRefusesAndSaysThatTheRequestIsTriedAgain() {
        api.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                Response.ok(releases(api.url() + "/download/server.jar")));

        try (var log = new LogRecorder()) {
            fetch(Map.of("GITHUB_TOKEN", TOKEN), new RecordingReporter());

            var lines = log.lines();
            assertEquals(4, lines.size(), log.output());
            assertTrue(lines.get(0).startsWith("FINE GitHub API request " + api.url() + RELEASES_PATH
                    + "?per_page=100 with the token of the environment variable GITHUB_TOKEN: HTTP 401 in "),
                    lines.get(0));
            assertEquals("WARNING GitHub releases of owner/repo: GitHub API refused the token of the environment "
                    + "variable GITHUB_TOKEN (HTTP 401): the token is invalid or has expired. Set GITHUB_TOKEN to a "
                    + "valid token, or unset it.", lines.get(1));
            assertEquals("INFO GitHub releases of owner/repo: trying again anonymously, since GitHub refused the "
                    + "token of the environment variable GITHUB_TOKEN", lines.get(2));
            assertTrue(lines.get(3).startsWith("FINE GitHub API request " + api.url() + RELEASES_PATH
                    + "?per_page=100 anonymously: HTTP 200 in "), lines.get(3));
            assertNoToken(log, TOKEN);
        }
    }

    @Test
    void theLogWarnsOfTheRateLimit() {
        api.answer(RELEASES_PATH, rateLimit(403, 60, RESET));

        try (var log = new LogRecorder()) {
            fetch(Map.of(), new RecordingReporter());

            assertEquals("WARNING Cannot load the GitHub releases of owner/repo: GitHub API rate limit reached: 60 "
                            + "requests an hour (HTTP 403). It resets at " + localTime(RESET) + " (local time)."
                            + RAISE_LIMIT + " limit to 5,000 requests an hour.",
                    log.lines().get(1), log.output());
        }
    }

    @Test
    void theLogNeverHasTheToken() {
        for (var response : List.of(Response.ok(releases(api.url() + "/download/server.jar")), error(401),
                error(403), error(404), error(500), rateLimit(403, 5000, RESET),
                new Response(403, Map.of("Retry-After", "60"), ""),
                new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL + "&t=" + TOKEN), ""),
                new Response(403, Map.of("X-Accepted-GitHub-Permissions", "contents=read"), ""))) {
            for (String token : List.of(TOKEN, TOKEN + "\nX-Injected: " + OTHER_TOKEN)) {
                api.answer(RELEASES_PATH, response);

                try (var log = new LogRecorder()) {
                    fetch(Map.of("GITHUB_TOKEN", token), new RecordingReporter());

                    assertFalse(log.lines().isEmpty());
                    assertNoToken(log, TOKEN);
                    assertNoToken(log, OTHER_TOKEN);
                    assertFalse(log.output().contains("Bearer"), log.output());
                }
            }
        }
    }

    @Test
    void theTokenOfTheSettingsIsSentWithoutTheVariables() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of(), () -> " " + TOKEN + " ", new RecordingAccounts(null), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
        assertEquals("> Loading GitHub releases: " + api.url() + RELEASES_PATH + "?per_page=100 (with the token of "
                + "the LSP4IJ settings)", reporter.lines().get(0));
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void theVariablesTakePrecedenceOverTheSettingsAndTheAccount() {
        for (String variable : List.of("GITHUB_TOKEN", "GH_TOKEN")) {
            try (var server = new LocalHttpServer()) {
                server.answer(RELEASES_PATH, Response.ok(releases(server.url() + "/download/server.jar")));
                var settings = new RecordingSettings(OTHER_TOKEN);
                var accounts = new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN));

                var reporter = new RecordingReporter();
                new GitHubAssetFetcher("owner", "repo", server.url(), Map.of(variable, TOKEN)::get, settings, accounts)
                        .getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), reporter);

                assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), server.requests(), variable);
                assertTrue(reporter.output().contains("(with the token of the environment variable " + variable + ")"),
                        reporter.output());
                assertEquals(0, settings.reads, "the Password Safe must not be read when " + variable + " is set");
                assertEquals(List.of(), accounts.apiUrls, "the account must not be asked when " + variable + " is set");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void theSettingsTakePrecedenceOverTheAccount() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));
        var accounts = new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN));

        var reporter = new RecordingReporter();
        fetch(Map.of("GITHUB_TOKEN", " ", "GH_TOKEN", ""), () -> TOKEN, accounts, reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN)), api.requests());
        assertEquals(List.of(), accounts.apiUrls, "the account must not be asked when the settings have a token");
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void theTokenOfTheAccountIsSentWithoutTheVariablesNorTheSettings() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));
        var accounts = new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN));

        var reporter = new RecordingReporter();
        // An empty setting, as after its field was cleared, leaves it to the account
        String downloadUrl = fetch(Map.of(), () -> " ", accounts, reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + ACCOUNT_TOKEN)), api.requests());
        assertEquals(List.of(api.url()), accounts.apiUrls, "the account must be asked for its token on the server of "
                + "the GitHub API only");
        assertEquals("> Loading GitHub releases: " + api.url() + RELEASES_PATH + "?per_page=100 (with the token of "
                + "the IDE's GitHub account octocat)", reporter.lines().get(0));
        assertNoToken(reporter, ACCOUNT_TOKEN);
    }

    @Test
    void noTokenIsSentWithoutAnySource() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of(), () -> null, new RecordingAccounts(null), reporter);

        assertEquals(List.of(new Request(RELEASES_PATH, null)), api.requests());
        // As before
        assertEquals("> Loading GitHub releases: " + api.url() + RELEASES_PATH + "?per_page=100",
                reporter.lines().get(0));
    }

    @Test
    void aSourceWhichFailsIsSkipped() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        try (var log = new LogRecorder()) {
            fetch(Map.of(), () -> {
                throw new IllegalStateException("The Password Safe is locked");
            }, new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN)), new RecordingReporter());

            assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + ACCOUNT_TOKEN)), api.requests());
            assertTrue(log.lines().stream().anyMatch(line -> line.startsWith(
                    "WARNING Cannot read the GitHub token of the LSP4IJ settings\njava.lang.IllegalStateException: "
                            + "The Password Safe is locked")), log.output());
        }
    }

    @Test
    void aTokenOfTheSettingsWithALineBreakInsideIsNotSentNorShown() {
        api.answer(RELEASES_PATH, Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        fetch(Map.of(), () -> TOKEN + "\nX-Injected: " + OTHER_TOKEN,
                new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN)), reporter);

        assertEquals("The token of the LSP4IJ settings has characters which a GitHub token doesn't have: LSP4IJ "
                + "doesn't send it to GitHub", reporter.lines().get(0));
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + ACCOUNT_TOKEN)), api.requests());
        assertNoToken(reporter, TOKEN);
        assertNoToken(reporter, OTHER_TOKEN);
    }

    @Test
    void aTokenOfTheSettingsThatGitHubRefusesIsNamedAndNotNeededForAPublicRepository() {
        api.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        String downloadUrl = fetch(Map.of(), () -> TOKEN, new RecordingAccounts(null), reporter);

        assertEquals(api.url() + "/download/server.jar", downloadUrl);
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null)),
                api.requests(), "the request must be tried again once, without the token");
        assertEquals("Error while loading GitHub releases: GitHub API refused the token of the LSP4IJ settings "
                        + "(HTTP 401)" + SETTINGS_RENEWAL + RETRY,
                reporter.lines().get(1).strip());
        assertNoToken(reporter, TOKEN);
    }

    @Test
    void aTokenOfTheAccountThatGitHubRefusesIsNamedAndNotNeededForAPublicRepository() {
        api.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                Response.ok(releases(api.url() + "/download/server.jar")));

        var reporter = new RecordingReporter();
        try (var log = new LogRecorder()) {
            String downloadUrl = fetch(Map.of(), () -> null,
                    new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN)), reporter);

            assertEquals(api.url() + "/download/server.jar", downloadUrl);
            assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + ACCOUNT_TOKEN),
                    new Request(RELEASES_PATH, null)), api.requests());
            assertEquals("Error while loading GitHub releases: GitHub API refused the token of the IDE's GitHub "
                            + "account octocat (HTTP 401): the token is invalid or has expired. Log in to the account "
                            + "again in Settings | Version Control | GitHub, or remove it there." + RETRY,
                    reporter.lines().get(1).strip());
            assertTrue(log.lines().contains("INFO GitHub releases of owner/repo: trying again anonymously, since GitHub "
                    + "refused the token of the IDE's GitHub account octocat"), log.output());
            assertNoToken(reporter, ACCOUNT_TOKEN);
            assertNoToken(log, ACCOUNT_TOKEN);
        }
    }

    @Test
    void theRateLimitOfATokenOfTheSettingsOrOfTheAccountNamesIt() {
        for (var source : List.of(Map.entry("the LSP4IJ settings", new AccountToken("unused", TOKEN)),
                Map.entry("the IDE's GitHub account octocat", new AccountToken("octocat", ACCOUNT_TOKEN)))) {
            try (var server = new LocalHttpServer()) {
                server.answer(RELEASES_PATH, rateLimit(403, 5000, RESET), rateLimit(403, 60, RESET));
                boolean settings = source.getKey().equals("the LSP4IJ settings");
                AccountToken token = source.getValue();

                var reporter = new RecordingReporter();
                assertNull(new GitHubAssetFetcher("owner", "repo", server.url(), name -> null,
                        () -> settings ? token.token() : null, new RecordingAccounts(settings ? null : token))
                        .getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), reporter));

                assertEquals("Error while loading GitHub releases: GitHub API rate limit reached for the token of "
                                + source.getKey() + ": 5000 requests an hour (HTTP 403). It resets at "
                                + localTime(RESET) + " (local time)." + RETRY,
                        reporter.lines().get(1).strip());
                assertNoToken(reporter, token.token());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void theLogNamesTheSourceOfTheTokenAndNeverHasIt() {
        api.answer(RELEASES_PATH, new Response(200,
                Map.of("X-RateLimit-Limit", "5000", "X-RateLimit-Remaining", "4999"),
                releases(api.url() + "/download/server.jar")));

        try (var log = new LogRecorder()) {
            var fetcher = new GitHubAssetFetcher("owner", "repo", api.url(), name -> null, () -> null,
                    new RecordingAccounts(new AccountToken("octocat", ACCOUNT_TOKEN)));
            fetcher.getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), new RecordingReporter());

            assertTrue(log.lines().get(0).startsWith("FINE GitHub API request " + api.url() + RELEASES_PATH
                    + "?per_page=100 with the token of the IDE's GitHub account octocat: HTTP 200 in "), log.output());
            assertEquals("the GitHub API, with the token of the IDE's GitHub account octocat", fetcher.getSource());
            assertNoToken(log, ACCOUNT_TOKEN);
            assertFalse(log.output().contains("Bearer"), log.output());
        }
    }

    static void assertNoToken(@NotNull LogRecorder log, @NotNull String token) {
        assertFalse(log.output().contains(token), "the log must not have the token");
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
     * Returns the download URL of the asset {@code server.jar} of the latest release of {@code owner/repo}, which a new
     * fetcher finds through the local GitHub API, with the given environment variables, token of the LSP4IJ settings
     * and GitHub accounts of the IDE.
     */
    private @Nullable String fetch(@NotNull Map<String, String> environment,
                                   @NotNull Supplier<String> settingsToken,
                                   @NotNull GitHubAccountTokenProvider accounts,
                                   @NotNull Reporter reporter) {
        return new GitHubAssetFetcher("owner", "repo", api.url(), environment::get, settingsToken, accounts)
                .getDownloadUrl(new GitHubAssetFetcher.AssetMatcher("server.jar"), reporter);
    }

    /**
     * The GitHub token of the LSP4IJ settings, which counts how often the Password Safe is read.
     */
    static final class RecordingSettings implements Supplier<String> {

        private final @Nullable String token;
        private int reads;

        RecordingSettings(@Nullable String token) {
            this.token = token;
        }

        @Override
        public @Nullable String get() {
            reads++;
            return token;
        }
    }

    /**
     * The GitHub accounts of the IDE, which have the given token for any server, and record which servers they are
     * asked for.
     */
    static final class RecordingAccounts implements GitHubAccountTokenProvider {

        private final @Nullable AccountToken token;
        private final List<String> apiUrls = new ArrayList<>();

        RecordingAccounts(@Nullable AccountToken token) {
            this.token = token;
        }

        @Override
        public @Nullable AccountToken findToken(@NotNull String apiUrl) {
            apiUrls.add(apiUrl);
            return token;
        }
    }

    /**
     * Returns a response of the GitHub API with the given HTTP status code, which isn't the rate limit.
     */
    private static @NotNull Response error(int code) {
        return new Response(code, Map.of(), "{\"message\": \"failed\"}");
    }

    /**
     * Returns a response of the GitHub API at the given rate limit, which resets at the given time, in seconds since
     * the epoch.
     */
    static @NotNull Response rateLimit(int code, int limit, long reset) {
        return new Response(code, Map.of(
                "X-RateLimit-Limit", "" + limit,
                "X-RateLimit-Remaining", "0",
                "X-RateLimit-Used", "" + limit,
                "X-RateLimit-Reset", "" + reset,
                "X-RateLimit-Resource", "core"),
                "{\"message\": \"API rate limit exceeded for 127.0.0.1.\"}");
    }

    /**
     * Returns the given time, in seconds since the epoch, in the time zone of the JVM, as {@code 14:35:07}.
     */
    static @NotNull String localTime(long epochSecond) {
        return LocalTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_TIME);
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

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.RELEASES_PATH;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.RESET;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.SETTINGS_RENEWAL;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.SSO_URL;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.TOKEN;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.localTime;
import static com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcherTest.releases;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A download task with a {@code github} source sends the GitHub token of the environment to the GitHub API, and not
 * with the download of the asset, and its console never shows the token, and says when the rate limit of the GitHub
 * API resets (#2142). When the download has no {@code url} to fall back to, the notification of the failed installation
 * says it too, and names where a token that GitHub refused comes from: the environment or the LSP4IJ settings (#2188).
 */
public class GitHubDownloadTaskTest {

    private static final String ASSET_PATH = "/download/v1.0/server.jar";

    private static final int TIMEOUT = 30;

    // What the notification says of the rate limit of the GitHub API without a token
    private static final String RATE_LIMIT = "GitHub API rate limit reached: 60 requests an hour (HTTP 403). It resets "
            + "at " + localTime(RESET) + " (local time)." + GitHubAssetFetcherTest.RAISE_LIMIT
            + " limit to 5,000 requests an hour.";

    // The same, in the HTML of the notification
    private static final String HTML_RATE_LIMIT = html(RATE_LIMIT);

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
        boolean installed = createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(context);

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
        boolean installed = createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(context);

        assertFalse(installed, "the download has no url to fall back to");
        assertTrue(context.output().contains("Error while loading GitHub releases: "), context.output());
        assertNoToken(context);
    }

    @Test
    public void testADownloadWhoseTokenGitHubRefusesInstallsWithoutIt() throws Exception {
        server.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                Response.ok(releases(server.url() + ASSET_PATH)));
        server.answer(ASSET_PATH, Response.ok("server"));

        var context = new RecordingContext();
        boolean installed = createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(context);

        assertTrue(installed, context.output());
        assertEquals("server", Files.readString(outputDir.resolve("server.jar")));
        assertEquals(List.of(new Request(RELEASES_PATH, "Bearer " + TOKEN), new Request(RELEASES_PATH, null),
                new Request(ASSET_PATH, null)), server.requests());
        assertTrue(context.errors().stream().anyMatch(error -> error.contains(
                        "GitHub API refused the token of the environment variable GITHUB_TOKEN (HTTP 401)")
                        && error.contains(GitHubAssetFetcherTest.RETRY)),
                "the console must say that the token was refused: " + context.output());
        assertNoToken(context);
    }

    @Test
    public void testTheConsoleSaysWhenTheRateLimitResetsAndFallsBackToTheUrl() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));
        server.answer(ASSET_PATH, Response.ok("server"));

        var context = new RecordingContext();
        boolean installed = createTask(Map.of(), server.url() + ASSET_PATH).run(context);

        assertTrue(installed, context.output());
        assertEquals("server", Files.readString(outputDir.resolve("server.jar")));
        assertTrue(context.errors().stream().anyMatch(error -> error.startsWith(
                        "Error while loading GitHub releases: GitHub API rate limit reached: 60 requests an hour "
                                + "(HTTP 403). It resets at " + localTime(RESET) + " (local time)."
                                + GitHubAssetFetcherTest.RAISE_LIMIT)),
                "the console must show the rate limit as an error: " + context.output());
        assertTrue(context.output().contains("fallback to the 'url' JSON property"), context.output());
    }

    @Test
    public void testTheNotificationOfADownloadWithoutUrlSaysWhenTheRateLimitResets() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));

        var context = new RecordingContext();
        var notification = install(createTask(Map.of(), null), context);

        assertEquals(NotificationType.ERROR, notification.getType());
        assertEquals("Installation of <code>test</code> failed.", notification.getTitle());
        assertEquals("<p>" + HTML_RATE_LIMIT + "</p>", notification.getContent());
        assertTrue(context.errors().stream().anyMatch(error -> error.contains(RATE_LIMIT)),
                "the console must show the rate limit as before: " + context.output());
    }

    @Test
    public void testTheNotificationOfADownloadWhichFallsBackToItsUrlIsUnchanged() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));
        server.answer(ASSET_PATH, Response.ok("server"));

        var notification = install(createTask(Map.of(), server.url() + ASSET_PATH), new RecordingContext());

        assertEquals(NotificationType.INFORMATION, notification.getType());
        assertEquals("Installation of <code>test</code> completed successfully.", notification.getTitle());
        // As before: the one message of the download, which doesn't say what the console says of the rate limit
        assertTrue(notification.getContent().startsWith("Server has been downloaded correctly in '"),
                notification.getContent());
        assertFalse(notification.getContent().contains("rate limit"), notification.getContent());
    }

    @Test
    public void testTheNotificationSaysThatGitHubRefusedTheTokenBeforeTheRateLimit() throws Exception {
        server.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                GitHubAssetFetcherTest.rateLimit(403, 60, RESET));

        var context = new RecordingContext();
        var notification = install(createTask(Map.of("GITHUB_TOKEN", TOKEN), null), context);

        assertEquals("<p>GitHub API refused the token of the environment variable GITHUB_TOKEN (HTTP 401): the token "
                        + "is invalid or has expired. Set GITHUB_TOKEN to a valid token, or unset it.</p>"
                        + "<p>" + HTML_RATE_LIMIT + "</p>",
                notification.getContent());
        assertNoToken(context);
    }

    @Test
    public void testTheNotificationNamesTheTokenOfTheSettingsWhichGitHubRefused() throws Exception {
        server.answer(RELEASES_PATH, new Response(401, Map.of(), "{\"message\": \"Bad credentials\"}"),
                GitHubAssetFetcherTest.rateLimit(403, 60, RESET));

        var context = new RecordingContext();
        var notification = install(createTask(Map.of(), () -> TOKEN, null), context);

        assertEquals("<p>GitHub API refused the token of the LSP4IJ settings (HTTP 401)" + html(SETTINGS_RENEWAL) + "</p>"
                        + "<p>" + HTML_RATE_LIMIT + "</p>",
                notification.getContent());
        assertNoToken(context);
    }

    @Test
    public void testTheNotificationEscapesTheLinkWhichAuthorizesTheToken() throws Exception {
        // A link with a second parameter, whose & the HTML of the notification escapes
        server.answer(RELEASES_PATH,
                new Response(403, Map.of("X-GitHub-SSO", "required; url=" + SSO_URL + "&organization=owner"), ""),
                new Response(404, Map.of(), ""));

        var notification = install(createTask(Map.of("GITHUB_TOKEN", TOKEN), null), new RecordingContext());

        assertEquals("<p>GitHub API refused the token of the environment variable GITHUB_TOKEN (HTTP 403): the "
                        + "organization owner enforces SAML single sign-on, and the token isn&#39;t authorized for it. "
                        + "Authorize it at " + SSO_URL + "&amp;organization=owner, a link which expires after an "
                        + "hour.</p><p>GitHub API returned HTTP 404: the repository owner/repo was not found, or it is "
                        + "private.</p>",
                notification.getContent());
    }

    @Test
    public void testTheNotificationSaysOnceThatTheRepositoryWasNotFound() throws Exception {
        server.answer(RELEASES_PATH, new Response(404, Map.of(), ""));

        var notification = install(createTask(Map.of("GITHUB_TOKEN", TOKEN), null), new RecordingContext());

        assertEquals("<p>GitHub API returned HTTP 404: the repository owner/repo was not found, or it is private and "
                        + "the token of the environment variable GITHUB_TOKEN can&#39;t read it.</p>",
                notification.getContent());
    }

    @Test
    public void testTheNotificationOfAnAssetNotFoundIsUnchangedAfterATokenWhichGitHubRefused() throws Exception {
        // The releases load without the token, and have no asset for the download
        server.answer(RELEASES_PATH, new Response(401, Map.of(), ""),
                Response.ok("[{\"tag_name\": \"v1.0\", \"prerelease\": false, \"assets\": []}]"));

        var notification = install(createTask(Map.of("GITHUB_TOKEN", TOKEN), null), new RecordingContext());

        assertEquals(NotificationType.ERROR, notification.getType());
        assertTrue(notification.getContent().startsWith("<p>Unable to retrieve the download URL for your platform "),
                notification.getContent());
        assertFalse(notification.getContent().contains("refused the token"), notification.getContent());
    }

    @Test
    public void testTheFileOfADownloadWhoseUrlHasAQueryIsNamedAfterItsPath() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));
        server.answer(ASSET_PATH, Response.ok("server"));
        var context = new RecordingContext();

        // The query, here with a token, isn't part of the name of the file, which a '?' can't be in on Windows
        boolean installed = createTask(Map.of(), server.url() + ASSET_PATH + "?access_token=" + TOKEN + "#top").run(context);

        assertTrue(installed, context.output());
        try (var files = Files.list(outputDir)) {
            assertEquals(List.of("server.jar"), files.map(file -> file.getFileName().toString()).toList());
        }
        assertEquals("server", Files.readString(outputDir.resolve("server.jar")));
    }

    @Test
    public void testTheLogHasOneLinePerDownloadWithWhereItsUrlComesFrom() throws Exception {
        server.answer(RELEASES_PATH, Response.ok(releases(server.url() + ASSET_PATH)));
        server.answer(ASSET_PATH, Response.ok("server"));

        try (var log = new LogRecorder()) {
            assertTrue(createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(new RecordingContext()));

            assertEquals(1, infoAndWarnings(log).size(), log.output());
            assertTrue(infoAndWarnings(log).get(0).matches(Pattern.quote("INFO Installer 'test' downloaded "
                            + server.url() + ASSET_PATH + " (6 bytes) in ") + "\\d+" + Pattern.quote(" ms; its URL "
                            + "comes from the GitHub API, with the token of the environment variable GITHUB_TOKEN (")
                            + "\\d+" + Pattern.quote(" ms)")),
                    log.output());
            assertNoToken(log);
        }
    }

    @Test
    public void testTheLogSaysThatADownloadWhoseTokenGitHubRefusesIsAnonymous() throws Exception {
        server.answer(RELEASES_PATH, new Response(401, Map.of(), ""), Response.ok(releases(server.url() + ASSET_PATH)));
        server.answer(ASSET_PATH, Response.ok("server"));

        try (var log = new LogRecorder()) {
            assertTrue(createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(new RecordingContext()));

            var lines = infoAndWarnings(log);
            assertEquals(3, lines.size(), log.output());
            assertTrue(lines.get(0).startsWith("WARNING GitHub releases of owner/repo: GitHub API refused the token "),
                    lines.get(0));
            assertTrue(lines.get(1).startsWith("INFO GitHub releases of owner/repo: trying again anonymously"),
                    lines.get(1));
            assertTrue(lines.get(2).contains("; its URL comes from the GitHub API, anonymously, since GitHub refused "
                    + "the token of the environment variable GITHUB_TOKEN ("), lines.get(2));
            assertNoToken(log);
        }
    }

    @Test
    public void testTheLogSaysThatADownloadFallsBackToItsUrl() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));
        server.answer(ASSET_PATH, Response.ok("server"));

        try (var log = new LogRecorder()) {
            assertTrue(createTask(Map.of(), server.url() + ASSET_PATH).run(new RecordingContext()));

            var lines = infoAndWarnings(log);
            assertEquals(3, lines.size(), log.output());
            assertTrue(lines.get(0).startsWith("WARNING Cannot load the GitHub releases of owner/repo: GitHub API "
                    + "rate limit reached: 60 requests an hour (HTTP 403)."), lines.get(0));
            assertEquals("INFO Installer 'test': no download URL from the GitHub API; falling back to the 'url' of the "
                    + "download: " + server.url() + ASSET_PATH, lines.get(1));
            assertTrue(lines.get(2).startsWith("INFO Installer 'test' downloaded " + server.url() + ASSET_PATH
                    + " (6 bytes) in "), lines.get(2));
            assertTrue(lines.get(2).contains(
                    "; its URL comes from its 'url', since its 'github' or 'maven' gave none ("), lines.get(2));
        }
    }

    @Test
    public void testTheLogHasNoSecretOfTheUrlOfADownload() throws Exception {
        server.answer(RELEASES_PATH, GitHubAssetFetcherTest.rateLimit(403, 60, RESET));
        server.answer(ASSET_PATH, Response.ok("server"), new Response(500, Map.of(), ""));
        String url = server.url() + ASSET_PATH + "?access_token=" + TOKEN;

        try (var log = new LogRecorder()) {
            // The fallback to the url of the download, which downloads, and then fails
            assertTrue(createTask(Map.of(), url).run(new RecordingContext()));
            assertFalse(createTask(Map.of(), url).run(new RecordingContext()));

            assertFalse(log.output().contains(TOKEN), "The log must not have the token of the URL: " + log.output());
            var lines = infoAndWarnings(log);
            String redacted = server.url() + ASSET_PATH + "?access_token=<redacted>";
            assertTrue(lines.stream().anyMatch(line -> line.endsWith("falling back to the 'url' of the download: "
                    + redacted)), log.output());
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("INFO Installer 'test' downloaded "
                    + redacted + " (6 bytes) in ")), log.output());
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("WARNING Installer 'test': the download of "
                    + redacted + " failed\n")), log.output());
        }
    }

    @Test
    public void testTheLogWarnsOfAFailedDownload() throws Exception {
        server.answer(RELEASES_PATH, Response.ok(releases(server.url() + ASSET_PATH)));
        server.answer(ASSET_PATH, new Response(500, Map.of(), ""));

        try (var log = new LogRecorder()) {
            assertFalse(createTask(Map.of("GITHUB_TOKEN", TOKEN), null).run(new RecordingContext()));

            var lines = infoAndWarnings(log);
            assertEquals(1, lines.size(), log.output());
            assertTrue(lines.get(0).startsWith("WARNING Installer 'test': the download of " + server.url() + ASSET_PATH
                    + " failed\n"), lines.get(0));
            assertNoToken(log);
        }
    }

    /**
     * Returns the records of the log which IntelliJ writes by default, of the levels INFO and above.
     */
    private static @NotNull List<String> infoAndWarnings(@NotNull LogRecorder log) {
        return log.lines().stream().filter(line -> !line.startsWith("FINE ")).toList();
    }

    private static void assertNoToken(@NotNull LogRecorder log) {
        assertFalse(log.output().contains(TOKEN), "the log must not have the token");
        assertFalse(log.output().contains("Bearer"), "the log must not have the Authorization header");
    }

    /**
     * Returns a download task which downloads the asset {@code server.jar} of the latest release of
     * {@code owner/repo}, which it finds through the local GitHub API with the given environment variables, or else
     * the given URL, into the output directory.
     */
    private @NotNull DownloadTask createTask(@NotNull Map<String, String> environment, @Nullable String url) {
        return createTask(environment, () -> null, url);
    }

    /**
     * Returns a download task which downloads the asset {@code server.jar} of the latest release of
     * {@code owner/repo}, which it finds through the local GitHub API with the given environment variables and token
     * of the LSP4IJ settings, or else the given URL, into the output directory.
     */
    private @NotNull DownloadTask createTask(@NotNull Map<String, String> environment,
                                             @NotNull Supplier<String> settingsToken,
                                             @Nullable String url) {
        var fetcher = new GitHubAssetFetcher("owner", "repo", server.url(), environment::get, settingsToken);
        var assetFetcherInfo = new DownloadTask.AssetFetcherInfo(fetcher,
                GitHubAssetFetcher.RELEASE_MATCHER,
                new GitHubAssetFetcher.AssetMatcher("server.jar"));
        var outputInfo = new DownloadTask.OutputInfo(outputDir.toString(), null, false, false);
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        return new DownloadTask(null, "download", null, null, url, assetFetcherInfo, outputInfo, descriptor);
    }

    /**
     * Installs with an installer whose run is the given download task, as the Installer tab does, and returns the
     * notification of the installation.
     */
    private static @NotNull Notification install(@NotNull DownloadTask task,
                                                 @NotNull RecordingContext context) throws Exception {
        var notification = new CompletableFuture<Notification>();
        Disposable disposable = Disposer.newDisposable();
        try {
            ApplicationManager.getApplication().getMessageBus().connect(disposable)
                    .subscribe(Notifications.TOPIC, new Notifications() {
                        @Override
                        public void notify(@NotNull Notification given) {
                            notification.complete(given);
                        }
                    });
            var installer = task.getServerInstallerDeclaration();
            installer.setRun(task);
            ServerInstallerManager.getInstance().install(installer, context);
            return notification.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("The installation gave no notification in " + TIMEOUT + " seconds", e);
        } finally {
            Disposer.dispose(disposable);
        }
    }

    /**
     * Returns the given text as the HTML of the notification has it.
     */
    private static @NotNull String html(@NotNull String text) {
        return text.replace("&", "&amp;").replace("'", "&#39;");
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
        private final List<String> errors = new CopyOnWriteArrayList<>();

        RecordingContext() {
            super(null, InstallerAction.RUN);
        }

        @Override
        public void print(@Nullable String message,
                          @NotNull ConsoleViewContentType contentType,
                          @NotNull Level logLevel) {
            if (message != null) {
                output.add(message);
                if (contentType == ConsoleViewContentType.ERROR_OUTPUT) {
                    errors.add(message);
                }
            }
            super.print(message, contentType, logLevel);
        }

        @NotNull String output() {
            return String.join("\n", output);
        }

        @NotNull List<String> errors() {
            return errors;
        }
    }
}

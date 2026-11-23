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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.util.EnvironmentUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class to fetch the download URL of a GitHub asset from the releases of a given repository.
 *
 * <p>This class queries the GitHub API to retrieve the list of releases and selects an asset
 * based on customizable filtering criteria using {@link Function} filters.</p>
 */
public class GitHubAssetFetcher implements AssetFetcher {

    /**
     * Default filter that matches only non-prerelease releases.
     */
    public static final Function<JsonObject, Boolean> RELEASE_MATCHER = new ReleaseMatcher(false);

    /**
     * Filter that matches only prerelease releases.
     */
    public static final Function<JsonObject, Boolean> PRERELEASE_MATCHER = new ReleaseMatcher(true);

    /**
     * Implementation of {@link Function} to filter releases based on whether they are prereleases.
     */
    public static class ReleaseMatcher implements Function<JsonObject, Boolean> {

        private final boolean prerelease;

        /**
         * Creates a release filter that accepts releases based on the prerelease flag.
         *
         * @param prerelease true to accept only prerelease releases, false otherwise
         */
        public ReleaseMatcher(boolean prerelease) {
            this.prerelease = prerelease;
        }

        /**
         * Applies the filter to a given release JSON object.
         *
         * @param release the JSON object representing a release
         * @return true if the release matches the prerelease criteria, false otherwise
         */
        @Override
        public Boolean apply(JsonObject release) {
            return prerelease == JSONUtils.getBoolean(release, "prerelease");
        }
    }

    /**
     * Implementation of {@link Function} to filter assets (files) within a release
     * by either a simple substring match or a glob pattern.
     */
    public static class AssetMatcher implements Function<JsonObject, Boolean> {

        private final @Nullable String assertPattern;
        private final Pattern globPattern;

        /**
         * Constructs an asset filter using either a simple substring or glob pattern.
         *
         * <p>If the pattern contains '*', it is treated as a glob pattern, otherwise as a substring.</p>
         *
         * @param assertPattern the pattern to match asset names against (glob or substring)
         */
        public AssetMatcher(@NotNull String assertPattern) {
            if (assertPattern.contains("*")) {
                this.assertPattern = null;
                this.globPattern = compileGlobToRegex(assertPattern);
            } else {
                this.assertPattern = assertPattern;
                this.globPattern = null;
            }
        }

        /**
         * Applies the filter to a given asset JSON object.
         *
         * @param asset the JSON object representing an asset (file) in the release
         * @return true if the asset name matches the filter pattern, false otherwise
         */
        @Override
        public Boolean apply(JsonObject asset) {
            String name = asset.get("name").getAsString();
            if (globPattern != null) {
                return globPattern.matcher(name).matches();
            }
            if (assertPattern != null) {
                return name.toLowerCase().contains(assertPattern.toLowerCase());
            }
            return false;
        }

        /**
         * Converts a simple glob pattern to a compiled regular expression.
         *
         * <p>Currently supports only '*' as a wildcard matching any sequence of characters.</p>
         *
         * @param glob the glob pattern to convert
         * @return the compiled regex {@link Pattern}
         */
        private static Pattern compileGlobToRegex(String glob) {
            // Escape '.' and replace '*' with '.*' for regex matching
            String regex = glob
                    .replace(".", "\\.")    // Escape dot character
                    .replace("*", ".*");    // Replace '*' with regex wildcard
            return Pattern.compile(regex);
        }
    }

    /**
     * The URL of the GitHub API.
     */
    private static final String GITHUB_API_URL = "https://api.github.com";

    /**
     * The environment variables which can hold a GitHub token, in order of precedence: {@code GITHUB_TOKEN}, and
     * {@code GH_TOKEN}, which the gh CLI reads. With a token, the GitHub API allows 5,000 requests an hour, instead of
     * 60 an hour by IP address (#2142).
     */
    private static final List<String> TOKEN_VARIABLES = List.of("GITHUB_TOKEN", "GH_TOKEN");

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // A permission of X-Accepted-GitHub-Permissions, as contents=read
    private static final Pattern PERMISSION = Pattern.compile("[a-z_]+=[a-z_]+");

    // The characters of a URL, which a header could otherwise put in the console, as a line break
    private static final Pattern URL_CHARACTERS = Pattern.compile("[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    private final @NotNull String owner;
    private final @NotNull String repository;
    private final @NotNull String apiUrl;
    private final @NotNull Function<String, String> environment;
    private @Nullable JsonArray releases;

    /**
     * Constructs a GitHubAssetFetcher for a given repository owner and name.
     *
     * @param owner the GitHub repository owner
     * @param repository the GitHub repository name
     */
    public GitHubAssetFetcher(@NotNull String owner, @NotNull String repository) {
        // The environment of the IDE, as the commands of the exec tasks get it: on macOS, with the variables of the
        // login shell when the IDE was started from the Dock
        this(owner, repository, GITHUB_API_URL, EnvironmentUtil::getValue);
    }

    /**
     * Constructs a GitHubAssetFetcher for a given repository owner and name, which requests the given GitHub API, and
     * finds a GitHub token in the given environment.
     *
     * @param owner       the GitHub repository owner
     * @param repository  the GitHub repository name
     * @param apiUrl      the URL of the GitHub API, without a trailing slash
     * @param environment the value of an environment variable, by name, or null if it isn't set
     */
    GitHubAssetFetcher(@NotNull String owner,
                       @NotNull String repository,
                       @NotNull String apiUrl,
                       @NotNull Function<String, String> environment) {
        this.owner = owner;
        this.repository = repository;
        this.apiUrl = apiUrl;
        this.environment = environment;
    }

    /**
     * Finds the download URL for an asset in the latest non-prerelease release matching the given asset filter.
     *
     * @param assetMatcher the filter to select the desired asset
     * @param reporter an object to report progress or status messages
     * @return the download URL of the matching asset, or null if none found
     */
    public @Nullable String getDownloadUrl(@NotNull Function<JsonObject, Boolean> assetMatcher,
                                           @NotNull Reporter reporter) {
        return getDownloadUrl(RELEASE_MATCHER, assetMatcher, reporter);
    }

    /**
     * Finds the download URL for an asset in the latest release matching the given release filter
     * and asset filter.
     *
     * @param releaseMatcher the filter to select the desired release (e.g. prerelease or not)
     * @param assetMatcher the filter to select the desired asset within the release
     * @param reporter an object to report progress or status messages
     * @return the download URL of the matching asset, or null if none found
     */
    @Override
    public @Nullable String getDownloadUrl(@NotNull Function<JsonObject, Boolean> releaseMatcher,
                                           @NotNull Function<JsonObject, Boolean> assetMatcher,
                                           @NotNull Reporter reporter) {
        try {
            JsonArray releases = getOrLoadReleases(reporter);
            if (releases == null) {
                return null;
            }
            reporter.setText("> Searching GitHub asset to download....");
            for (JsonElement releaseElem : releases) {
                JsonObject release = releaseElem.getAsJsonObject();
                if (!releaseMatcher.apply(release)) {
                    continue;
                }

                JsonArray assets = release.getAsJsonArray("assets");
                for (JsonElement assetElem : assets) {
                    JsonObject asset = assetElem.getAsJsonObject();
                    if (assetMatcher.apply(asset)) {
                        String downloadUrl = asset.get("browser_download_url").getAsString();
                        reporter.setText("Asset found " + downloadUrl);
                        return downloadUrl;
                    }
                }
                break; // only check the latest matching release
            }
            reporter.setText("No asset found to download....");
        }
        catch(Exception e) {
            // Exception swallowed silently (consider logging or reporting)
        }
        return null;
    }

    /**
     * Returns cached releases or loads them from GitHub if not already loaded.
     *
     * @param reporter an object to report progress or status messages
     * @return the JSON array of releases, or null if loading failed
     * @throws Exception if an error occurs during loading
     */
    private @Nullable JsonArray getOrLoadReleases(@NotNull Reporter reporter) throws Exception {
        if (releases != null) {
            return releases;
        }
        String json = fetchReleasesJson(reporter);
        if (json == null) {
            return null;
        }
        return releases = JsonParser.parseString(json).getAsJsonArray();
    }

    /**
     * Returns the URL of the GitHub API which lists the releases of the given repository, the newest first. The API
     * lists 30 releases by default, and up to 100 with {@code per_page}: a repository which publishes many
     * prereleases, as the nightly builds of Open-MBEE/OpenSysML, can push its latest release out of the first 30, and
     * the installer then installs its fallback URL, an older version (#1961).
     *
     * @param owner      the owner of the repository.
     * @param repository the repository.
     * @return the URL which lists the releases of the repository.
     */
    static @NotNull String releasesUrl(@NotNull String owner, @NotNull String repository) {
        return releasesUrl(GITHUB_API_URL, owner, repository);
    }

    private static @NotNull String releasesUrl(@NotNull String apiUrl,
                                               @NotNull String owner,
                                               @NotNull String repository) {
        return apiUrl + "/repos/" + owner + "/" + repository + "/releases?per_page=100";
    }

    /**
     * A GitHub token, and the environment variable which holds it. Its string doesn't show the token.
     *
     * @param variable the environment variable which holds the token.
     * @param value    the token.
     */
    private record Token(@NotNull String variable, @NotNull String value) {

        @Override
        public @NotNull String toString() {
            return "Token[" + variable + "]";
        }
    }

    /**
     * Returns the GitHub token of the first environment variable of {@link #TOKEN_VARIABLES} which is set and isn't
     * blank, without the spaces around it, or null if there is none. A value with a space or a control character
     * inside, which a token doesn't have, is skipped and reported, without the value: a line break in a header would
     * make {@link HttpURLConnection} throw an exception whose message shows it.
     *
     * @param reporter an object to report progress or status messages
     * @return the GitHub token, or null if there is none.
     */
    private @Nullable Token findToken(@NotNull Reporter reporter) {
        for (String variable : TOKEN_VARIABLES) {
            String value = environment.apply(variable);
            if (value == null || value.isBlank()) {
                continue;
            }
            value = value.strip();
            if (value.chars().allMatch(c -> c > ' ' && c < 0x7F)) {
                return new Token(variable, value);
            }
            reporter.setText("The environment variable " + variable
                    + " has characters which a GitHub token doesn't have: LSP4IJ doesn't send it to GitHub");
        }
        return null;
    }

    /**
     * Fetches the raw JSON string of releases from the GitHub API.
     *
     * @param reporter an object to report progress or status messages
     * @return the JSON string of releases, or null if an error occurred
     */
    private @Nullable String fetchReleasesJson(@NotNull Reporter reporter)  {
        String urlStr = releasesUrl(apiUrl, owner, repository);
        // Read at each request, after the IDE has loaded the environment of the login shell
        Token token = findToken(reporter);
        reporter.setText("> Loading GitHub releases: " + urlStr
                + (token != null ? " (with the token of the environment variable " + token.variable() + ")" : ""));

        try {
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            if (token != null) {
                // HttpURLConnection follows the redirects, and drops the header on a redirect to another host, port or
                // protocol: the token goes to the GitHub API only
                conn.setRequestProperty("Authorization", "Bearer " + token.value());
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String error = getErrorMessage(conn, responseCode, token);
                if (error != null) {
                    throw new GitHubApiError(error);
                }
                throw new RuntimeException("GitHub API returned HTTP " + responseCode);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        }
        catch(Exception e) {
            reporter.setText("Error while loading GitHub releases: ", e);
        }
        return null;
    }

    /**
     * Returns the message which explains the given error of the GitHub API when it's the rate limit, a token that
     * GitHub refused, or a repository that it didn't find, and null otherwise. The message never shows the token.
     *
     * @param conn         the connection, with the response of the GitHub API.
     * @param responseCode the HTTP status code of the response.
     * @param token        the GitHub token of the request, or null.
     * @return the message which explains the error, or null.
     */
    private @Nullable String getErrorMessage(@NotNull HttpURLConnection conn,
                                             int responseCode,
                                             @Nullable Token token) {
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED && token != null) {
            return "GitHub API refused the token of the environment variable " + token.variable()
                    + " (HTTP 401): the token is invalid or has expired. Set " + token.variable()
                    + " to a valid token, or unset it.";
        }
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 429) {
            String rateLimit = getRateLimitMessage(conn, responseCode, token);
            if (rateLimit != null) {
                return rateLimit;
            }
        }
        if (responseCode != HttpURLConnection.HTTP_FORBIDDEN && responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
            return null;
        }
        // A token which isn't authorized for an organization which enforces SAML single sign-on gets 403 or 404, and
        // X-GitHub-SSO: "required; url=<the URL which authorizes it>", or "partial-results; organizations=<ids>"
        String singleSignOn = conn.getHeaderField("X-GitHub-SSO");
        if (token != null && singleSignOn != null) {
            String url = getSingleSignOnUrl(singleSignOn, token);
            return "GitHub API refused the token of the environment variable " + token.variable() + " (HTTP "
                    + responseCode + "): the organization " + owner + " enforces SAML single sign-on, and the token "
                    + "isn't authorized for it. "
                    + (url != null ? "Authorize it at " + url + ", a link which expires after an hour."
                    : "Authorize it for " + owner + " in the settings of the token on GitHub.");
        }
        // As "contents=read", or "pull_requests=read,contents=read; issues=read,contents=read" for two sets of
        // permissions, either of which is enough
        List<List<String>> permissions = parsePermissions(conn.getHeaderField("X-Accepted-GitHub-Permissions"));
        if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            if (token == null || permissions.isEmpty()) {
                return null;
            }
            return "GitHub API refused the token of the environment variable " + token.variable()
                    + " (HTTP 403): the token lacks the " + formatPermissions(permissions) + ".";
        }
        // GitHub answers 404 for a private repository which the request can't read, so as not to show that it exists
        String notFound = "GitHub API returned HTTP 404: the repository " + owner + "/" + repository
                + " was not found, or it is private";
        if (token == null) {
            return notFound + ".";
        }
        return notFound + " and the token of the environment variable " + token.variable() + " can't read it."
                + (permissions.isEmpty() ? "" : " Reading it needs the " + formatPermissions(permissions) + ".");
    }

    /**
     * Returns the message which explains a 403 or 429 of the GitHub API when it's the rate limit, and null otherwise.
     * The GitHub API answers 403 or 429 at the rate limit, with {@code X-RateLimit-Remaining: 0} and the time when the
     * limit resets, in seconds since the epoch, in {@code X-RateLimit-Reset}, or, at a secondary rate limit, the
     * seconds to wait in {@code Retry-After}.
     *
     * @param conn         the connection, with the response of the GitHub API.
     * @param responseCode the HTTP status code of the response.
     * @param token        the GitHub token of the request, or null.
     * @return the message which explains the rate limit, or null.
     */
    private static @Nullable String getRateLimitMessage(@NotNull HttpURLConnection conn,
                                                        int responseCode,
                                                        @Nullable Token token) {
        boolean exhausted = "0".equals(conn.getHeaderField("X-RateLimit-Remaining"));
        String retryAfter = conn.getHeaderField("Retry-After");
        if (!exhausted && retryAfter == null) {
            return null;
        }
        StringBuilder message = new StringBuilder("GitHub API rate limit reached");
        if (token != null) {
            message.append(" for the token of the environment variable ").append(token.variable());
        }
        String limit = conn.getHeaderField("X-RateLimit-Limit");
        if (exhausted && limit != null) {
            message.append(": ").append(limit).append(" requests an hour");
        }
        message.append(" (HTTP ").append(responseCode).append(").");
        Long reset = exhausted ? parseLong(conn.getHeaderField("X-RateLimit-Reset")) : null;
        Long seconds = parseLong(retryAfter);
        if (reset != null) {
            message.append(" It resets at ").append(formatLocalTime(Instant.ofEpochSecond(reset))).append('.');
        } else if (seconds != null) {
            message.append(" Try again in ").append(seconds).append(" seconds, at ")
                    .append(formatLocalTime(Instant.now().plusSeconds(seconds))).append('.');
        }
        if (token == null) {
            message.append(" Setting the environment variable GITHUB_TOKEN of the IDE to a GitHub token raises the")
                    .append(exhausted ? " limit to 5,000 requests an hour." : " limit.");
        }
        return message.toString();
    }

    /**
     * Returns the URL of the given {@code X-GitHub-SSO} header, which authorizes the token for the organization, or
     * null if it has none, or one which isn't an https URL of the characters of a URL. The URL identifies the
     * authorization request, not the token; one which has the token isn't returned.
     */
    private static @Nullable String getSingleSignOnUrl(@NotNull String singleSignOn, @NotNull Token token) {
        for (String part : singleSignOn.split(";")) {
            String url = part.strip();
            if (url.startsWith("url=")) {
                url = url.substring("url=".length());
                return url.startsWith("https://") && URL_CHARACTERS.matcher(url).matches()
                        && !url.contains(token.value()) ? url : null;
            }
        }
        return null;
    }

    /**
     * Returns the sets of permissions of the given {@code X-Accepted-GitHub-Permissions} header, any of which is
     * enough, or an empty list if there is no header, or one which isn't made of permissions such as
     * {@code contents=read}.
     */
    private static @NotNull List<List<String>> parsePermissions(@Nullable String header) {
        if (header == null) {
            return List.of();
        }
        List<List<String>> sets = Arrays.stream(header.split(";"))
                .map(set -> Arrays.stream(set.split(","))
                        .map(String::strip)
                        .filter(permission -> !permission.isEmpty())
                        .toList())
                .filter(set -> !set.isEmpty())
                .toList();
        boolean valid = sets.stream()
                .flatMap(List::stream)
                .allMatch(permission -> PERMISSION.matcher(permission).matches());
        return valid ? sets : List.of();
    }

    /**
     * Returns the given sets of permissions, as {@code permission contents=read}, or
     * {@code permissions pull_requests=read and contents=read, or issues=read and contents=read}.
     */
    private static @NotNull String formatPermissions(@NotNull List<List<String>> sets) {
        boolean many = sets.size() > 1 || sets.get(0).size() > 1;
        return (many ? "permissions " : "permission ") + sets.stream()
                .map(set -> String.join(" and ", set))
                .collect(Collectors.joining(", or "));
    }

    private static @Nullable Long parseLong(@Nullable String value) {
        try {
            return value != null ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static @NotNull String formatLocalTime(@NotNull Instant instant) {
        return TIME_FORMATTER.withZone(ZoneId.systemDefault()).format(instant) + " (local time)";
    }

    /**
     * An error of the GitHub API whose message says what happened and what to do. The console shows the message
     * without a stack trace, which wouldn't help.
     */
    private static class GitHubApiError extends Exception {

        GitHubApiError(@NotNull String message) {
            super(message, null, false, false);
        }

        @Override
        public @NotNull String toString() {
            return getMessage();
        }
    }

}

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
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;
import java.util.regex.Pattern;

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

    private final @NotNull String owner;
    private final @NotNull String repository;
    private @Nullable JsonArray releases;

    /**
     * Constructs a GitHubAssetFetcher for a given repository owner and name.
     *
     * @param owner the GitHub repository owner
     * @param repository the GitHub repository name
     */
    public GitHubAssetFetcher(@NotNull String owner, @NotNull String repository) {
        this.owner = owner;
        this.repository = repository;
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
     * Fetches the raw JSON string of releases from the GitHub API.
     *
     * @param reporter an object to report progress or status messages
     * @return the JSON string of releases, or null if an error occurred
     */
    private @Nullable String fetchReleasesJson(@NotNull Reporter reporter)  {
        String urlStr = "https://api.github.com/repos/" + owner + "/" + repository + "/releases";
        reporter.setText("> Loading GitHub releases: " + urlStr);

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
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

}

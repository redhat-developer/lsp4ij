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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Utility class to fetch the download URL of a Maven artifact from Maven Central.
 */
public class MavenArtifactFetcher implements AssetFetcher {

    private final @NotNull String groupId;
    private final @NotNull String artifactId;
    private @Nullable JsonArray docs;

    public MavenArtifactFetcher(@NotNull String groupId, @NotNull String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    /**
     * Finds the download URL for an asset in the latest release matching the given release filter
     * and asset filter.
     *
     * @param releaseMatcher the filter to select the desired release (e.g. prerelease or not)
     * @param docMatcher     the filter to select the desired asset within the release
     * @param reporter       an object to report progress or status messages
     * @return the download URL of the matching asset, or null if none found
     */
    @Override
    public @Nullable String getDownloadUrl(@NotNull Function<JsonObject, Boolean> releaseMatcher,
                                           @NotNull Function<JsonObject, Boolean> docMatcher,
                                           @NotNull Reporter reporter) {
        try {
            JsonArray docs = getOrLoadDocs(reporter);
            if (docs == null) {
                return null;
            }
            reporter.setText("> Searching Maven artifact to download....");
            for (JsonElement docElem : docs) {
                JsonObject doc = docElem.getAsJsonObject();
                if (docMatcher.apply(doc)) {
                    String version = doc.get("latestVersion").getAsString();
                    String groupPath = groupId.replace('.', '/');
                    String url = "https://repo1.maven.org/maven2/" + groupPath + "/" + artifactId + "/" + version + "/" +
                            artifactId + "-" + version + ".jar";
                    reporter.setText("Artifact found " + url);
                    return url;
                }
            }
            reporter.setText("No matching artifact found....");
        } catch (Exception e) {
            reporter.setText("Error while searching Maven artifacts: ", e);
        }
        return null;
    }

    private @Nullable JsonArray getOrLoadDocs(@NotNull Reporter reporter) throws Exception {
        if (docs != null) {
            return docs;
        }
        String json = fetchDocsFromMavenCentral(reporter);
        if (json == null) {
            return null;
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return docs = root.getAsJsonObject("response").getAsJsonArray("docs");
    }

    private @Nullable String fetchDocsFromMavenCentral(@NotNull Reporter reporter) {
        String query = String.format("g:\"%s\" AND a:\"%s\"", groupId, artifactId);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String urlStr = "https://search.maven.org/solrsearch/select?q=" + encodedQuery + "&rows=20&wt=json";
        reporter.setText("> Loading Maven docs: " + urlStr);

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("Maven Central API returned HTTP " + responseCode);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            reporter.setText("Error while loading Maven docs: ", e);
        }
        return null;
    }
}

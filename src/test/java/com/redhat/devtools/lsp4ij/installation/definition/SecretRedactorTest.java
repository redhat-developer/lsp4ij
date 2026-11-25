/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The log of an installer gets the lines that it prints without their values which can be secrets (#2156).
 */
public class SecretRedactorTest {

    private static final String SECRET = "s3cr3t-Value_42";

    @Test
    public void testTheValueOfAnAuthorizationHeaderIsRedacted() {
        assertRedacted("Authorization: Bearer " + SECRET, "Authorization: <redacted>");
        assertRedacted("authorization=Basic " + SECRET + " done", "authorization=<redacted>");
        assertRedacted("curl -H 'Proxy-Authorization: token " + SECRET + "' https://example.com",
                "curl -H 'Proxy-Authorization: <redacted>' https://example.com");
        assertRedacted("the request had Bearer " + SECRET + ".", "the request had Bearer <redacted>");
        // A header with quotes around its name and its value, as in JSON, with any scheme
        assertRedacted("{\"Authorization\":\"Basic " + SECRET + "\"}", "{\"Authorization\":\"<redacted>\"}");
        assertRedacted("{'Proxy-Authorization': 'Bearer " + SECRET + "'}", "{'Proxy-Authorization': '<redacted>'}");
    }

    @Test
    public void testTheUserAndThePasswordOfAUrlAreRedacted() {
        assertRedacted("> Downloading asset: https://user:" + SECRET + "@example.com/server.zip",
                "> Downloading asset: https://<redacted>@example.com/server.zip");
        assertRedacted("git clone https://" + SECRET + "@github.com/owner/repo.git",
                "git clone https://<redacted>@github.com/owner/repo.git");
    }

    @Test
    public void testTheSecretParametersOfAUrlAreRedacted() {
        assertRedacted("https://example.com/server.zip?access_token=" + SECRET + "&version=1",
                "https://example.com/server.zip?access_token=<redacted>&version=1");
        assertRedacted("https://bucket.s3.amazonaws.com/server.zip?X-Amz-Credential=" + SECRET
                        + "&X-Amz-Signature=" + SECRET,
                "https://bucket.s3.amazonaws.com/server.zip?X-Amz-Credential=<redacted>&X-Amz-Signature=<redacted>");
        assertRedacted("https://account.blob.core.windows.net/c/server.zip?sv=2022&sig=" + SECRET,
                "https://account.blob.core.windows.net/c/server.zip?sv=2022&sig=<redacted>");
        assertRedacted("https://example.com/api?key=" + SECRET, "https://example.com/api?key=<redacted>");
    }

    @Test
    public void testTheSecretOptionsAndAssignmentsOfACommandAreRedacted() {
        assertRedacted("> server --token=" + SECRET + " --stdio", "> server --token=<redacted> --stdio");
        assertRedacted("> server --api-key " + SECRET + " --stdio", "> server --api-key <redacted> --stdio");
        assertRedacted("> server -password \"" + SECRET + " with spaces\" --stdio",
                "> server -password <redacted> --stdio");
        assertRedacted("> java -Dgithub.token=" + SECRET + " -jar server.jar",
                "> java -Dgithub.token=<redacted> -jar server.jar");
        assertRedacted("GITHUB_TOKEN=" + SECRET + " npm install", "GITHUB_TOKEN=<redacted> npm install");
        assertRedacted("//registry.npmjs.org/:_authToken = " + SECRET, "//registry.npmjs.org/:_authToken = <redacted>");
        assertRedacted("client_secret='" + SECRET + "'", "client_secret=<redacted>");
        // A property of an installer, as InstallerContext.putProperty logs it
        assertRedacted("${api.token}=" + SECRET, "${api.token}=<redacted>");
    }

    @Test
    public void testAGitHubTokenIsRedacted() {
        assertRedacted("token ghp_" + "a".repeat(36) + " found", "token <redacted> found");
        assertRedacted("github_pat_" + "B".repeat(22) + "_" + "c".repeat(59), "<redacted>");
    }

    @Test
    public void testTheValueOfAnEnvironmentVariableWhichHoldsASecretIsRedacted() {
        var environment = Map.of("GITHUB_TOKEN", " " + SECRET + " ",
                "NPM_CONFIG_PASSWORD", "another-secret",
                "PATH", "/usr/bin:/bin",
                "SOME_TOKEN_FLAG", "true");
        assertEquals("echo <redacted> <redacted> /usr/bin:/bin true",
                SecretRedactor.redact("echo " + SECRET + " another-secret /usr/bin:/bin true", environment));
    }

    @Test
    public void testALineWithoutASecretIsUnchanged() {
        for (String line : new String[]{
                "> Loading GitHub releases: https://api.github.com/repos/owner/repo/releases?per_page=100 "
                        + "(with the token of the environment variable GITHUB_TOKEN)",
                "GitHub API rate limit reached for the token of the environment variable GITHUB_TOKEN: 5000 "
                        + "requests an hour (HTTP 403).",
                "Setting the environment variable GITHUB_TOKEN of the IDE to a GitHub token raises the limit.",
                "GitHub API rate limit reached (HTTP 403). A GitHub token in Settings | Languages & Frameworks | "
                        + "Language Servers (LSP4IJ), or in the environment variable GITHUB_TOKEN of the IDE, raises "
                        + "the limit.",
                "> Loading GitHub releases: https://api.github.com/repos/owner/repo/releases?per_page=100 "
                        + "(with the token of the LSP4IJ settings)",
                "GitHub API refused the token of the IDE's GitHub account octocat (HTTP 401): the token is invalid "
                        + "or has expired.",
                "${server.command}=node server.js --stdio --token-file /home/user/token.txt",
                "> npm install --prefix /home/user/.lsp4ij/lsp typescript-language-server",
                "Asset found https://github.com/owner/repo/releases/download/v1.0/server.zip",
                "token_type=bearer",
                "- Step: Check the token"}) {
            assertRedacted(line, line);
        }
    }

    private static void assertRedacted(@NotNull String line, @NotNull String expected) {
        assertEquals(expected, SecretRedactor.redact(line, Map.of()));
    }
}

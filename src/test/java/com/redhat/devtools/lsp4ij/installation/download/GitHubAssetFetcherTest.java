/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lookup of a GitHub release asks the GitHub API for the most releases it gives on one page, 100 instead of 30,
 * so that the latest release of a repository with many prereleases is among them (#1961).
 */
public class GitHubAssetFetcherTest {

    @Test
    void theReleasesAreAskedForOneHundredAtATime() {
        assertEquals("https://api.github.com/repos/Open-MBEE/OpenSysML/releases?per_page=100",
                GitHubAssetFetcher.releasesUrl("Open-MBEE", "OpenSysML"));
    }
}

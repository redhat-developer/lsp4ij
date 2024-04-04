/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files;

import org.junit.Test;

import java.net.URI;

import static com.redhat.devtools.lsp4ij.URIFactory.createFileUri;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test with pattern matches.
**/
public class PathPatternMatcher_matchesTest {

    @Test
    public void node_modules() {
        URI fileUri = createFileUri("/node_module/");
        assertNoMatches(fileUri, "{**/node_modules/**,**/.git/**,**/bower_components/**}");
        fileUri = createFileUri("/node_modules/");
        assertMatches(fileUri, "{**/node_modules/**,**/.git/**,**/bower_components/**}");
    }

    @Test
    public void clojure() {
        URI fileUri = createFileUri("foo.cl");
        //assertNoMatches(fileUri, "**/*.{clj,cljs,cljc,cljd,edn,bb,clj_kondo}");
        fileUri = URI.create("file:///C:/Users/azerr/foo/src/foo.clj"); //)createFileUri("foo.clj");
        assertMatches(fileUri, "**/*.{clj,cljs,cljc,cljd,edn,bb,clj_kondo}");
        fileUri = createFileUri("foo/src/foo.clj");
        assertMatches(fileUri, "**/*.{clj,cljs,cljc,cljd,edn,bb,clj_kondo}");
    }

    private static void assertMatches(URI fileUri, String pattern) {
        assertTrue("'" + fileUri.toASCIIString() + "' file uri should match '" + pattern + "'", new PathPatternMatcher(pattern).matches(fileUri));
    }

    private static void assertNoMatches(URI fileUri, String pattern) {
        assertFalse("'" + fileUri.toASCIIString() + "' file uri should not match '" + pattern + "'", new PathPatternMatcher(pattern).matches(fileUri));
    }
}

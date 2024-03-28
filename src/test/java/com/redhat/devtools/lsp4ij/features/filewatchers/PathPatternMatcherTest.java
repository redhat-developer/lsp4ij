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
package com.redhat.devtools.lsp4ij.features.filewatchers;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test with pattern expansion.
**/
public class PathPatternMatcherTest {

    @Test
    public void noExpansion() {
        assertExpandPatterns("foo", "foo");
    }

    @Test
    public void oneExpansion() {
        assertExpandPatterns("**/foo", "foo", "**/foo");
    }

    @Test
    public void twoExpansion() {
        assertExpandPatterns("**/foo/**", "foo", "**/foo", "foo/**", "**/foo/**");
    }

    @Test
    public void sixExpansion() {
        assertExpandPatterns("{**/node_modules/**,**/.git/**,**/bower_components/**}",
                "{node_modules,.git,bower_components}",
                "{node_modules,.git,bower_components/**}",
                "{node_modules,.git,**/bower_components}",
                "{node_modules,.git,**/bower_components/**}",
                "{node_modules,.git/**,bower_components}",
                "{node_modules,.git/**,bower_components/**}",
                "{node_modules,.git/**,**/bower_components}",
                "{node_modules,.git/**,**/bower_components/**}",
                "{node_modules,**/.git,bower_components}",
                "{node_modules,**/.git,bower_components/**}",
                "{node_modules,**/.git,**/bower_components}",
                "{node_modules,**/.git,**/bower_components/**}",
                "{node_modules,**/.git/**,bower_components}",
                "{node_modules,**/.git/**,bower_components/**}",
                "{node_modules,**/.git/**,**/bower_components}",
                "{node_modules,**/.git/**,**/bower_components/**}",
                "{node_modules/**,.git,bower_components}",
                "{node_modules/**,.git,bower_components/**}",
                "{node_modules/**,.git,**/bower_components}",
                "{node_modules/**,.git,**/bower_components/**}",
                "{node_modules/**,.git/**,bower_components}",
                "{node_modules/**,.git/**,bower_components/**}",
                "{node_modules/**,.git/**,**/bower_components}",
                "{node_modules/**,.git/**,**/bower_components/**}",
                "{node_modules/**,**/.git,bower_components}",
                "{node_modules/**,**/.git,bower_components/**}",
                "{node_modules/**,**/.git,**/bower_components}",
                "{node_modules/**,**/.git,**/bower_components/**}",
                "{node_modules/**,**/.git/**,bower_components}",
                "{node_modules/**,**/.git/**,bower_components/**}",
                "{node_modules/**,**/.git/**,**/bower_components}",
                "{node_modules/**,**/.git/**,**/bower_components/**}",
                "{**/node_modules,.git,bower_components}",
                "{**/node_modules,.git,bower_components/**}",
                "{**/node_modules,.git,**/bower_components}",
                "{**/node_modules,.git,**/bower_components/**}",
                "{**/node_modules,.git/**,bower_components}",
                "{**/node_modules,.git/**,bower_components/**}",
                "{**/node_modules,.git/**,**/bower_components}",
                "{**/node_modules,.git/**,**/bower_components/**}",
                "{**/node_modules,**/.git,bower_components}",
                "{**/node_modules,**/.git,bower_components/**}",
                "{**/node_modules,**/.git,**/bower_components}",
                "{**/node_modules,**/.git,**/bower_components/**}",
                "{**/node_modules,**/.git/**,bower_components}",
                "{**/node_modules,**/.git/**,bower_components/**}",
                "{**/node_modules,**/.git/**,**/bower_components}",
                "{**/node_modules,**/.git/**,**/bower_components/**}",
                "{**/node_modules/**,.git,bower_components}",
                "{**/node_modules/**,.git,bower_components/**}",
                "{**/node_modules/**,.git,**/bower_components}",
                "{**/node_modules/**,.git,**/bower_components/**}",
                "{**/node_modules/**,.git/**,bower_components}",
                "{**/node_modules/**,.git/**,bower_components/**}",
                "{**/node_modules/**,.git/**,**/bower_components}",
                "{**/node_modules/**,.git/**,**/bower_components/**}",
                "{**/node_modules/**,**/.git,bower_components}",
                "{**/node_modules/**,**/.git,bower_components/**}",
                "{**/node_modules/**,**/.git,**/bower_components}",
                "{**/node_modules/**,**/.git,**/bower_components/**}",
                "{**/node_modules/**,**/.git/**,bower_components}",
                "{**/node_modules/**,**/.git/**,bower_components/**}",
                "{**/node_modules/**,**/.git/**,**/bower_components}",
                "{**/node_modules/**,**/.git/**,**/bower_components/**}");
    }

    private static void assertExpandPatterns(String pattern, String... expectedPatterns) {
        List<String> actual = PathPatternMatcher.expandPatterns(pattern);
        Collections.sort(actual);
        List<String> expected = Arrays.asList(expectedPatterns);
        Collections.sort(expected);
        assertArrayEquals("'" + pattern + "' pattern expansion should match [\"" + String.join("\",\"", actual) + "\"]",
                actual.toArray(new String[0]), expected.toArray(new String[0]));
    }
}

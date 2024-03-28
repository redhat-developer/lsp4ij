/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.filewatchers;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Path pattern matcher.
 */
public class PathPatternMatcher {

    record Parts(List<String> parts, List<Integer> cols) {}

    private List<PathMatcher> pathMatchers;
    private final String pattern;

    public PathPatternMatcher(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean matches(String uri) {
        try {
            return matches(new URI(uri));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean matches(URI uri) {
        if (pattern.isEmpty()) {
            return false;
        }
        if (pathMatchers == null) {
            createPathMatchers();
        }
        try {
            Path path = Paths.get(uri);
            for (PathMatcher pathMatcher : pathMatchers) {
                try {
                    if (pathMatcher.matches(path)) {
                        return true;
                    }
                } catch (Exception e) {
                    // Do nothing
                }
            }
        } catch (Exception e) {
            // Do nothing
        }
        return false;
    }

    private synchronized void createPathMatchers() {
        if (pathMatchers != null) {
            return;
        }
        String glob = pattern.replace("\\", "/");
        // As Java NIO glob doesn't support **/, /** as optional
        // we need to expand the pattern, ex: **/foo -> foo, **/foo.
        List<String> expandedPatterns = expandPatterns(glob);
        List<PathMatcher> pathMatchers = new ArrayList<>();
        for (var expandedPattern : expandedPatterns) {
            try {
                PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + expandedPattern);
                pathMatchers.add(pathMatcher);
            } catch (Exception e) {
                // Do nothing
            }
        }
        this.pathMatchers = pathMatchers;
    }

    /**
     * Expand the given pattern. ex: ** /foo -> foo, ** /foo.
     *
     * @param pattern the pattern
     * @return the given pattern.
     */
    static List<String> expandPatterns(String pattern) {
        Parts parts = getParts(pattern);
        if (parts != null) {
            // tokenize pattern ex : **/foo/** --> [**/, foo, /**]
            List<String> expanded = new ArrayList<>();
            // generate combinations array with 0,1 according to the number of **/, /**
            // ex: **/foo/** (number=2) --> [[0, 0], [0, 1], [1, 0], [1, 1]
            List<int[]> combinations = generateCombinations(parts.cols().size());
            for (int[] combination : combinations) {
                // Clone tokenized pattern (ex : [**/, foo, /**])
                List<String> expand = new ArrayList<>(parts.parts());
                for (int i = 0; i < combination.length; i++) {
                    // Loop for current combination (ex : [0, 1])
                    if (combination[i] == 0) {
                        // When 0,  replace **/, /** with ""
                        // ex : [**/, foo, /**] --> ["", foo, "/**"]
                        int col = parts.cols().get(i);
                        expand.set(col, "");
                    }
                }
                // ["", foo, "/**"] --> foo/**
                expanded.add(String.join("", expand));
            }
            return expanded;
        }
        return Collections.singletonList(pattern);
    }


    private static Parts getParts(String pattern) {
        int from = 0;
        int index = getNextIndex(pattern, from);
        if (index != -1) {
            List<Integer> cols = new ArrayList<>();
            List<String> parts = new ArrayList<>();
            while (index != -1) {
                String s = pattern.substring(from, index);
                if (!s.isEmpty()) {
                    parts.add(s);
                }
                cols.add(parts.size());
                from = index + 3;
                parts.add(pattern.substring(index, from));
                index += 3;
                index = getNextIndex(pattern, index);
            }
            parts.add(pattern.substring(from));
            return new Parts(parts, cols);
        }
        return null;
    }

    private static int getNextIndex(String pattern, int fromIndex) {
        int startSlashIndex = pattern.indexOf("**/", fromIndex);
        int endSlashIndex = pattern.indexOf("/**", fromIndex);
        if (startSlashIndex != -1 || endSlashIndex != -1) {
            if (startSlashIndex == -1) {
                return endSlashIndex;
            }
            if (endSlashIndex == -1) {
                return startSlashIndex;
            }
            return Math.min(startSlashIndex, endSlashIndex);
        }
        return -1;
    }

    public static List<int[]> generateCombinations(int N) {
        List<int[]> combinations = new ArrayList<>();
        generateCombinationsHelper(N, new int[N], 0, combinations);
        return combinations;
    }

    private static void generateCombinationsHelper(int N, int[] combination, int index, List<int[]> combinations) {
        if (index == N) {
            combinations.add(combination.clone());
        } else {
            combination[index] = 0;
            generateCombinationsHelper(N, combination, index + 1, combinations);
            combination[index] = 1;
            generateCombinationsHelper(N, combination, index + 1, combinations);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof PathPatternMatcher other) {
            if (!Objects.deepEquals(pathMatchers, other.pathMatchers)) {
                return false;
            }
            return Objects.equals(pattern, other.getPattern());
        }
        return false;
    }
}
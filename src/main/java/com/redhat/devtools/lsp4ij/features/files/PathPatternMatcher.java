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
package com.redhat.devtools.lsp4ij.features.files;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    private final @NotNull String pattern;
    private final @Nullable Path basePath;
    private List<PathMatcher> pathMatchers;
    public PathPatternMatcher(@NotNull String pattern,
                              @Nullable Path basePath) {
        this.pattern = pattern;
        this.basePath = basePath;
    }

    /**
     * Creates a PathPatternMatcher by automatically extracting the base path from the pattern.
     * <p>
     * The base path is the portion of the pattern before any glob characters (*, ?, [, {).
     * The pattern is then adjusted to be relative to this base path.
     *
     * @param fullPattern the glob pattern with optional absolute base path
     * @return a new PathPatternMatcher with extracted base path and relative pattern
     */
    public static @NotNull PathPatternMatcher fromPattern(@NotNull final String fullPattern,
                                                          @Nullable Path defaultBasePath) {
        // Find the first occurrence of glob characters: * ? [ {
        int firstGlobChar = findFirstGlobCharacter(fullPattern);

        if (firstGlobChar == -1) {
            // For literal patterns (no glob characters) that are relative (e.g., "node_modules"),
            // we assume they are relative to the workspace root. By setting defaultBasePath,
            // the FileSystemWatcherManager can relativize absolute file paths against it
            // and match the relative pattern correctly.
            Path patternPath = Paths.get(fullPattern.replace('\\', '/'));
            if (!patternPath.isAbsolute()) {
                return new PathPatternMatcher(fullPattern, defaultBasePath);
            }
            // No glob characters, treat entire string as pattern
            return new PathPatternMatcher(fullPattern, null);
        }

        if (firstGlobChar == 0) {
            // Pattern starts with a glob character (e.g., "**/*.rs")
            return new PathPatternMatcher(fullPattern, defaultBasePath);
        }

        // Find the last path separator before the first glob character
        String beforeGlob = fullPattern.substring(0, firstGlobChar);

        // Support both forward slash and backslash
        int lastSeparator = Math.max(
                beforeGlob.lastIndexOf('/'),
                beforeGlob.lastIndexOf('\\')
        );

        if (lastSeparator == -1) {
            // No separator found before glob, no valid base path
            return new PathPatternMatcher(fullPattern, defaultBasePath);
        }

        // Extract base path (everything before the last separator before glob)
        String basePathStr = fullPattern.substring(0, lastSeparator);

        // Extract relative pattern (everything after the base path)
        // Skip the separator itself (lastSeparator + 1)
        String relativePattern = fullPattern.substring(lastSeparator + 1);

        try {
            // Normalize separators for Path
            Path extractedPath = Paths.get(basePathStr.replace('\\', '/'));
            // If the extracted base path is relative (e.g., "some" from "some/**/*.js"),
            // we resolve it against defaultBasePath so it can be compared against absolute paths.
            // Otherwise, absolute paths being tested won't match the relative base path prefix.
            Path basePath;
            if (extractedPath.isAbsolute()) {
                basePath = extractedPath;
            } else if (defaultBasePath != null) {
                basePath = defaultBasePath.resolve(extractedPath);
            } else {
                basePath = extractedPath;
            }
            return new PathPatternMatcher(relativePattern, basePath);
        } catch (Exception e) {
            // If path creation fails, return with original pattern and no base
            return new PathPatternMatcher(fullPattern, defaultBasePath);
        }
    }

    /**
     * Finds the index of the first glob metacharacter in the string.
     * Glob characters: * ? [ {
     *
     * @param pattern the pattern to search
     * @return the index of the first glob character, or -1 if none found
     */
    private static int findFirstGlobCharacter(@NotNull String pattern) {
        int minIndex = Integer.MAX_VALUE;

        int asterisk = pattern.indexOf('*');
        if (asterisk != -1 && asterisk < minIndex) {
            minIndex = asterisk;
        }

        int question = pattern.indexOf('?');
        if (question != -1 && question < minIndex) {
            minIndex = question;
        }

        int bracket = pattern.indexOf('[');
        if (bracket != -1 && bracket < minIndex) {
            minIndex = bracket;
        }

        int brace = pattern.indexOf('{');
        if (brace != -1 && brace < minIndex) {
            minIndex = brace;
        }

        return minIndex == Integer.MAX_VALUE ? -1 : minIndex;
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

    public @NotNull String getPattern() {
        return pattern;
    }

    public @Nullable Path getBasePath() {
        return basePath;
    }

    public boolean matches(@NotNull URI uri) {
        return internalMatches(uri, null);
    }

    public boolean matches(@NotNull Path path) {
        return internalMatches(null, path);
    }

    private boolean internalMatches(@Nullable URI uri,
                                    @Nullable Path path) {
        if (pattern.isEmpty()) {
            return false;
        }
        if (pathMatchers == null) {
            createPathMatchers();
        }
        try {
            path = path == null ? Paths.get(uri) : path;
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

    record Parts(@NotNull List<String> parts, @NotNull List<Integer> cols) {
    }
}
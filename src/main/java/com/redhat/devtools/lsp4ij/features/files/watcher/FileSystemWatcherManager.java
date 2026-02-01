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
package com.redhat.devtools.lsp4ij.features.files.watcher;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LSP file system manager which matches a given URI by using LSP {@link FileSystemWatcher}.
 */
public class FileSystemWatcherManager {

    private static final int WatchKindAny = 7;

    private final Map<String, List<FileSystemWatcher>> registry;
    private final @Nullable Path basePath;

    private Set<FileSystemWatcher> fileSystemWatchers;

    private Map<Integer, List<PathPatternMatcher>> pathPatternMatchers;

    public FileSystemWatcherManager(@NotNull Project project) {
        this(getProjectBasePath(project));
    }

    public FileSystemWatcherManager(@Nullable Path basePath) {
        this.basePath = basePath;
        this.registry = new HashMap<>();
    }

    private static @Nullable Path getProjectBasePath(@NotNull Project project) {
        var baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir != null) {
            try {
                return baseDir.toNioPath();
            } catch (UnsupportedOperationException e) {
                // TempFileSystem (used in light tests) doesn't support toNioPath()
            }
        }
        // Fallback to project.getBasePath(), which returns a path string like "temp:///src"
        // in light tests using TempFileSystem. Path.of() handles this without throwing.
        String basePath = project.getBasePath();
        return basePath != null ? Path.of(basePath) : null;
    }

    private static void tryAddingMatcher(@NotNull PathPatternMatcher matcher,
                                         @NotNull Map<Integer, List<PathPatternMatcher>> matchers,
                                         @Nullable Integer watcherKind,
                                         int kind) {
        if (!isWatchKind(watcherKind, kind)) {
            return;
        }
        List<PathPatternMatcher> matchersForKind = matchers.computeIfAbsent(kind, k -> new ArrayList<>());
        matchersForKind.add(matcher);
    }

    /**
     * Checks if the combined value contains a specific kind.
     */
    private static boolean isWatchKind(Integer watcherKind, int kind) {
        return watcherKind == null || (watcherKind & kind) != 0;
    }

    @Nullable
    private static PathPatternMatcher getPathPatternMatcher(@NotNull FileSystemWatcher fileSystemMatcher,
                                                            @Nullable Path basePath) {
        Either<String, RelativePattern> globPattern = fileSystemMatcher.getGlobPattern();
        if (globPattern != null) {
            if (globPattern.isLeft()) {
                String pattern = globPattern.getLeft();
                if (StringUtils.isBlank(pattern)) {
                    // Invalid pattern, ignore the watcher
                    return null;
                }
                return new PathPatternMatcher(pattern, basePath);
            } else {
                RelativePattern relativePattern = globPattern.getRight();
                if (relativePattern != null) {
                    // Implement relative pattern like glob string pattern
                    // by waiting for finding a concrete use case.
                    String pattern = relativePattern.getPattern();
                    if (StringUtils.isBlank(pattern)) {
                        // Invalid pattern, ignore the watcher
                        return null;
                    }
                    Path relativeBasePath = getRelativeBasePath(relativePattern.getBaseUri());
                    if (relativeBasePath == null) {
                        // Invalid baseUri, ignore the watcher
                        return null;
                    }
                    return new PathPatternMatcher(pattern, relativeBasePath);
                }
            }
        }
        return null;
    }

    private static @Nullable Path getRelativeBasePath(@Nullable Either<WorkspaceFolder, String> baseUri) {
        if (baseUri == null) {
            return null;
        }
        String baseDir = null;
        if (baseUri.isRight()) {
            baseDir = baseUri.getRight();
        } else if (baseUri.isLeft()) {
            var workspaceFolder = baseUri.getLeft();
            baseDir = workspaceFolder != null ? workspaceFolder.getUri() : null;
        }
        if (StringUtils.isBlank(baseDir)) {
            return null;
        }
        try {
            return Paths.get(URI.create(baseDir));
        } catch(Exception e) {
            return null;
        }
    }

    /**
     * Register the file system watcher list with the given id.
     *
     * @param id       the id.
     * @param watchers the file system watcher list.
     */
    public void registerFileSystemWatchers(String id, List<FileSystemWatcher> watchers) {
        if (watchers == null) {
            return;
        }
        synchronized (registry) {
            registry.put(id, watchers);
            reset();
        }
    }

    /**
     * Unregister the file system watcher list with the given id.
     *
     * @param id the id.
     */
    public void unregisterFileSystemWatchers(String id) {
        synchronized (registry) {
            registry.remove(id);
            reset();
        }
    }

    private void reset() {
        this.fileSystemWatchers = registry
                .values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        pathPatternMatchers = null;
    }

    /**
     * Returns the list of LSP file system watchers and null otherwise.
     *
     * @return the list of LSP file system watchers and null otherwise.
     */
    public Set<FileSystemWatcher> getFileSystemWatchers() {
        return fileSystemWatchers;
    }

    /**
     * Returns true if there are some file system watchers and false otherwise.
     *
     * @return true if there are some file system watchers and false otherwise.
     */
    public boolean hasFilePatterns() {
        return fileSystemWatchers != null && !fileSystemWatchers.isEmpty();
    }

    public boolean hasFilePatternsFor(int kind) {
        if (!hasFilePatterns()) {
            return false;
        }

        // Ensure pattern matchers are initialized before use
        computePatternMatchersIfNeed();

        var matchers = pathPatternMatchers.get(kind);
        return matchers != null && !matchers.isEmpty();
    }
    /**
     * Returns true if the given uri matches a pattern for the given watch kind and false otherwise.
     *
     * @param uri  the uri to match.
     * @param kind the watch kind ({@link WatchKind#Create}, {@link WatchKind#Change}, {@link WatchKind#Delete} or 7 (for any))
     * @return true if the given uri matches a pattern for the given watch kind and false otherwise.
     */
    public boolean isMatchFilePattern(@Nullable URI uri, int kind) {
        // If no URI or no patterns are registered, there can be no match
        if (uri == null || !hasFilePatterns()) {
            return false;
        }

        // Ensure pattern matchers are initialized before use
        computePatternMatchersIfNeed();

        // Cache: basePath -> relative path if included, false otherwise
        Map<Path, Either<Path, Boolean>> basePathToRelativePath = new HashMap<>();

        try {
            // Convert the URI to a Path for matching
            Path path = Paths.get(uri);

            // Match against the given kind or the "any" kind
            return match(path, kind, basePathToRelativePath)
                    || match(path, WatchKindAny, basePathToRelativePath);

        } catch (Exception e) {
            // Any failure in URI-to-Path conversion or matching is treated as "no match"
            return false;
        }
    }

    private void computePatternMatchersIfNeed() {
        if (pathPatternMatchers == null) {
            computePatternMatchers();
        }
    }

    private synchronized void computePatternMatchers() {
        if (pathPatternMatchers != null) {
            return;
        }
        Map<Integer, List<PathPatternMatcher>> matchers = new HashMap<>();
        for (var fileSystemMatcher : fileSystemWatchers) {
            PathPatternMatcher matcher = getPathPatternMatcher(fileSystemMatcher, basePath);
            if (matcher != null) {
                Integer kind = fileSystemMatcher.getKind();
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Create);
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Change);
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Delete);
            }
        }
        pathPatternMatchers = matchers;
    }

    /**
     * Checks whether the given {@link Path} matches any registered {@link PathPatternMatcher}
     * for the specified watch kind.
     *
     * <p>This method iterates through all pattern matchers for the given {@code kind},
     * checks if the provided path is under each matcher’s base path, and if so, applies the matcher
     * to the relative path.</p>
     *
     * <p>To optimize performance, a cache map is used to store intermediate results for base path checks:
     * <ul>
     *   <li><b>Key:</b> the base path of a matcher</li>
     *   <li><b>Value:</b> Either:
     *       <ul>
     *         <li>Left - the relative path if the path is under the base path</li>
     *         <li>Right(false) - indicates the path is not under this base path</li>
     *       </ul>
     *   </li>
     * </ul>
     * </p>
     *
     * @param path the file path to test, must not be {@code null}
     * @param kind the watch kind to check against (e.g., {@link WatchKind#Create},
     *             {@link WatchKind#Change}, {@link WatchKind#Delete}, or {@code WatchKindAny} for a wildcard match)
     * @param basePathToRelativePath a cache for storing relative paths or negative results
     * @return {@code true} if the path matches any pattern for the given kind, {@code false} otherwise
     */
    private boolean match(@NotNull Path path,
                          int kind,
                          @NotNull Map<Path, Either<Path, Boolean>> basePathToRelativePath) {

        // Retrieve all matchers registered for the given kind
        List<PathPatternMatcher> matchers = pathPatternMatchers.get(kind);
        if (matchers == null) {
            return false; // No matchers for this kind
        }

        // Iterate over each matcher
        for (var matcher : matchers) {
            // Check if the path is under the matcher’s base path
            var relativePath = matchBasePath(path, matcher.getBasePath(), basePathToRelativePath);
            if (relativePath != null) {
                // Apply the matcher to the relative path
                if (matcher.matches(matcher.getBasePath().relativize(path))) {
                    return true;
                }
            }
        }

        // No matcher matched
        return false;
    }

    /**
     * Checks whether the given {@link Path} is located under a specified base path.
     *
     * <p>If the path is under the base path, the relative path is returned and cached.
     * If the path is not under the base path, the result is cached as a negative match.</p>
     *
     * @param path the path to check, must not be {@code null}
     * @param basePath the base path to test against, may be {@code null}
     * @param basePathToRelativePath cache map to store computed results
     * @return the relative path between {@code basePath} and {@code path} if included,
     *         or {@code null} if the path is not under the base path
     */
    private @Nullable Path matchBasePath(@NotNull Path path,
                                         @Nullable Path basePath,
                                         @NotNull Map<Path, Either<Path, Boolean>> basePathToRelativePath) {
        if (basePath == null) {
            return null; // No base path to check
        }

        // Check cache first
        var matches = basePathToRelativePath.get(basePath);
        if (matches != null) {
            if (matches.isLeft()) {
                return matches.getLeft(); // Cached positive result
            }
            return null; // Cached negative result
        }

        // Compute for the first time
        if (path.startsWith(basePath)) {
            var relativePath = basePath.relativize(path);
            basePathToRelativePath.put(basePath, Either.forLeft(relativePath)); // Cache positive result
            return relativePath;
        }

        // Path is not under base path, cache negative result
        basePathToRelativePath.put(basePath, Either.forRight(Boolean.FALSE));
        return null;
    }

}

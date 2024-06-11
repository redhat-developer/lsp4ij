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

import com.redhat.devtools.lsp4ij.features.files.PathPatternMatcher;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LSP file system manager which matches a given URI by using LSP {@link FileSystemWatcher}.
 */
public class FileSystemWatcherManager {

    private static final int WatchKindAny = 7;

    private final Map<String, List<FileSystemWatcher>> registry;

    private Set<FileSystemWatcher> fileSystemWatchers;

    private Map<Integer, List<PathPatternMatcher>> pathPatternMatchers;

    public FileSystemWatcherManager() {
        this.registry = new HashMap<>();
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

    /**
     * Returns true if the given uri matches a pattern for the given watch kind and false otherwise.
     *
     * @param uri  the uri to match.
     * @param kind the watch kind ({@link WatchKind#Create}, {@link WatchKind#Change}, {@link WatchKind#Delete} or 7 (for any))
     * @return true if the given uri matches a pattern for the given watch kind and false otherwise.
     */
    public boolean isMatchFilePattern(@Nullable URI uri, int kind) {
        if (uri == null || !hasFilePatterns()) {
            return false;
        }
        computePatternMatchersIfNeed();
        return (match(uri, kind) || match(uri, WatchKindAny));
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
            PathPatternMatcher matcher = getPathPatternMatcher(fileSystemMatcher);
            if (matcher != null) {
                Integer kind = fileSystemMatcher.getKind();
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Create);
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Change);
                tryAddingMatcher(matcher, matchers, kind, WatchKind.Delete);
            }
        }
        pathPatternMatchers = matchers;
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
    private static PathPatternMatcher getPathPatternMatcher(FileSystemWatcher fileSystemMatcher) {
        Either<String, RelativePattern> globPattern = fileSystemMatcher.getGlobPattern();
        if (globPattern != null) {
            if (globPattern.isLeft()) {
                String pattern = globPattern.getLeft();
                return new PathPatternMatcher(pattern);
            } else {
                RelativePattern relativePattern = globPattern.getRight();
                if (relativePattern != null) {
                    // Implement relative pattern like glob string pattern
                    // by waiting for finding a concrete use case.
                    String pattern = relativePattern.getPattern();
                    return new PathPatternMatcher(pattern);
                }
            }
        }
        return null;
    }

    private boolean match(URI uri, int kind) {
        List<PathPatternMatcher> matchers = pathPatternMatchers.get(kind);
        if (matchers == null) {
            return false;
        }
        for (var matcher : matchers) {
            if (matcher.matches(uri)) {
                return true;
            }
        }
        return false;
    }

}

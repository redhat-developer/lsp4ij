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

import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LSP file system manager which matches a given URI by using LSP {@link FileSystemWatcher}.
 */
public class FileSystemWatcherManager {

    private static final int WatchKindAny = 7;

    private List<FileSystemWatcher> fileSystemWatchers;

    private Map<Integer, List<PathPatternMatcher>> pathPatternMatchers;

    /**
     * Update the list of LSP file system watchers.
     *
     * @param fileSystemWatchers list of file system watcher.
     */
    public void setFileSystemWatchers(List<FileSystemWatcher> fileSystemWatchers) {
        this.fileSystemWatchers = fileSystemWatchers;
        pathPatternMatchers = null;
    }

    /**
     * Returns the list of LSP file system watchers and null otherwise.
     *
     * @return the list of LSP file system watchers and null otherwise.
     */
    public List<FileSystemWatcher> getFileSystemWatchers() {
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
                Integer kind = getWatchKind(fileSystemMatcher);
                List<PathPatternMatcher> matchersForKind = matchers.computeIfAbsent(kind, k -> new ArrayList<>());
                matchersForKind.add(matcher);
            }
        }
        pathPatternMatchers = matchers;
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

    private static Integer getWatchKind(FileSystemWatcher watcher) {
        Integer kind = watcher.getKind();
        if (kind != null && (kind == WatchKind.Create || kind == WatchKind.Change || kind == WatchKind.Delete)) {
            return kind;
        }
        return WatchKindAny;
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

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

public class FileSystemWatcherManager {

    private static int WatchKindAny = 7;

    private List<FileSystemWatcher> fileSystemWatchers;

    private Map<Integer, List<PathPatternMatcher>> pathPatternMatchers;


    public void setFileSystemWatchers(List<FileSystemWatcher> fileSystemWatchers) {
        this.fileSystemWatchers = fileSystemWatchers;
        pathPatternMatchers = null;
    }

    public boolean hasFilePatterns() {
        return fileSystemWatchers != null && !fileSystemWatchers.isEmpty();
    }

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
            PathPatternMatcher matcher = null;
            Either<String, RelativePattern> globPattern = fileSystemMatcher.getGlobPattern();
            if (globPattern != null) {
                if (globPattern.isLeft()) {
                    String pattern = globPattern.getLeft();
                    matcher = new PathPatternMatcher().setPattern(pattern);
                } else {
                    RelativePattern relativePattern = globPattern.getRight();
                    if (relativePattern != null) {
                        String pattern = relativePattern.getPattern();
                        matcher = new PathPatternMatcher().setPattern(pattern);
                    }
                }
            }
            if (matcher != null) {
                Integer kind = getWatchKind(fileSystemMatcher);
                List<PathPatternMatcher> matchersForKind = matchers.get(kind);
                if (matchersForKind == null) {
                    matchersForKind = new ArrayList<>();
                    matchers.put(kind, matchersForKind);
                }
                matchersForKind.add(matcher);
            }
        }
        pathPatternMatchers = matchers;
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
        for(var matcher : matchers) {
            if (matcher.matches(uri)) {
                return true;
            }
        }
        return false;
    }
}

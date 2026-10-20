/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files.watcher;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes to the file system in watched roots outside the project become VFS events without an IDE focus change, and
 * the roots are released with their watchers.
 */
public class FileSystemWatchRootsTest extends BasePlatformTestCase {
    private Path directory;
    // the project, which the watched roots are outside of
    private Path project;
    private FileSystemWatchRoots roots;
    private final List<VFileEvent> events = new CopyOnWriteArrayList<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        directory = Files.createTempDirectory("lsp4ij-watch-").toRealPath();
        project = directory.resolve("project");
        roots = new FileSystemWatchRoots();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        Disposer.register(getTestRootDisposable(), roots);
        getProject().getMessageBus().connect(getTestRootDisposable())
                .subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
                    @Override
                    public void after(List<? extends VFileEvent> changes) {
                        events.addAll(changes);
                    }
                });
    }

    public void testExternalCreationModificationAndDeletionWhileFocused() throws Exception {
        Path watched = Files.createDirectories(directory.resolve("external"));
        roots.replace(Set.of(new FileSystemWatchRoots.Root(watched, true)));
        refresh();
        assertTrue(roots.watchRequestCount() > 0);
        Path source = watched.resolve("data.txt");
        Files.writeString(source, "before");
        refresh();
        assertEvent(source, VFileCreateEvent.class);
        events.clear();
        Files.writeString(source, "after, with a different size");
        refresh();
        assertEvent(source, VFileContentChangeEvent.class);
        events.clear();
        Files.delete(source);
        refresh();
        assertEvent(source, VFileDeleteEvent.class);
    }

    public void testInitiallyMissingNestedRootAndRecreation() throws Exception {
        Path watched = directory.resolve("generated/resources");
        roots.replace(Set.of(new FileSystemWatchRoots.Root(watched, true)));
        refresh();
        Files.createDirectories(watched);
        refresh();
        Path source = watched.resolve("data.txt");
        Files.writeString(source, "first");
        refresh();
        assertEvent(source, VFileCreateEvent.class);
        events.clear();
        Files.delete(source);
        Files.delete(watched);
        refresh();
        Files.createDirectory(watched);
        refresh();
        Files.writeString(source, "second");
        refresh();
        assertEvent(source, VFileCreateEvent.class);
    }

    public void testRecursiveRootLoadsPreviouslyUnknownChildDirectories() throws Exception {
        Path watched = Files.createDirectories(directory.resolve("external"));
        Files.createDirectories(watched.resolve("deep/nested"));
        roots.replace(Set.of(new FileSystemWatchRoots.Root(watched, true)));
        refresh();
        Path source = watched.resolve("deep/nested/data.txt");
        Files.writeString(source, "created");
        refresh();
        assertEvent(source, VFileCreateEvent.class);
    }

    public void testDuplicateRegistrationsRetainLeaseUntilLastUnregister() throws Exception {
        Path watched = Files.createDirectories(directory.resolve("external"));
        var manager = new FileSystemWatcherManager(project, roots);
        Disposer.register(getTestRootDisposable(), manager);
        var watchers = List.of(relative(watched, "**/*.txt"));
        manager.registerFileSystemWatchers("one", watchers);
        manager.registerFileSystemWatchers("two", watchers);
        refresh();
        int count = roots.watchRequestCount();
        assertTrue(count > 0);
        manager.unregisterFileSystemWatchers("one");
        assertEquals(count, roots.watchRequestCount());
        assertTrue(manager.isMatchFilePattern(watched.resolve("data.txt").toUri(), WatchKind.Create));
        manager.unregisterFileSystemWatchers("two");
        assertEquals(0, roots.watchRequestCount());
        assertFalse(manager.hasFilePatterns());
    }

    public void testClearAndDisposeReleaseLeasesAndDisposeRejectsLaterRegistrations() throws Exception {
        Path watched = Files.createDirectories(directory.resolve("external"));
        var manager = new FileSystemWatcherManager(project, roots);
        Disposer.register(getTestRootDisposable(), manager);
        var watchers = List.of(relative(watched, "**/*.txt"));
        manager.registerFileSystemWatchers("one", watchers);
        refresh();
        assertTrue(roots.watchRequestCount() > 0);
        manager.clear();
        assertEquals(0, roots.watchRequestCount());
        manager.registerFileSystemWatchers("next-connection", watchers);
        refresh();
        assertTrue(roots.watchRequestCount() > 0);
        manager.dispose();
        manager.registerFileSystemWatchers("late", watchers);
        refresh();
        assertEquals(0, roots.watchRequestCount());
        assertFalse(manager.hasFilePatterns());
    }

    public void testReplacedRootsDoNotJoinARefreshOfThePreviousGeneration() throws Exception {
        Path oldRoot = Files.createDirectories(directory.resolve("old"));
        Path newRoot = Files.createDirectories(directory.resolve("new"));
        var current = new AtomicReference<CompletableFuture<Void>>();
        WriteAction.run(() -> {
            // Hold write access so the old background pass cannot finish its read action.
            roots.replace(Set.of(new FileSystemWatchRoots.Root(oldRoot, true)));
            var previous = roots.refresh();
            roots.replace(Set.of(new FileSystemWatchRoots.Root(newRoot, true)));
            current.set(roots.refresh());
            assertNotSame("New roots need their own completed refresh", previous, current.get());
        });
        PlatformTestUtil.waitWithEventsDispatching("replacement root refresh", current.get()::isDone, 10);
        current.get().join();
        Path source = newRoot.resolve("data.txt");
        Files.writeString(source, "created");
        refresh();
        assertEvent(source, VFileCreateEvent.class);
    }

    public void testSymlinkRootIsNotTraversedOrWatched() throws Exception {
        Path target = Files.createDirectories(directory.resolve("target"));
        Path link = Files.createSymbolicLink(directory.resolve("link"), target);
        roots.replace(Set.of(new FileSystemWatchRoots.Root(link, true)));
        refresh();
        assertEquals(0, roots.watchRequestCount());
    }

    public void testARootBelowASymbolicLinkIsWatched() throws Exception {
        // as on macOS, where /tmp and /var are links
        Files.createDirectories(directory.resolve("real/external"));
        Path link = Files.createSymbolicLink(directory.resolve("alias"), directory.resolve("real"));
        Path watched = link.resolve("external");
        roots.replace(Set.of(new FileSystemWatchRoots.Root(watched, true)));
        refresh();
        assertTrue("a link above the root must not keep it from being watched", roots.watchRequestCount() > 0);
        Path source = watched.resolve("data.txt");
        Files.writeString(source, "created");
        refresh();
        Path real = source.toRealPath();
        assertTrue("Expected VFileCreateEvent for " + real + "; got " + events, events.stream().anyMatch(event ->
                event instanceof VFileCreateEvent && Files.exists(Path.of(event.getPath()))
                        && real.equals(toRealPath(Path.of(event.getPath())))));
    }

    public void testAGlobWithDotDotMatchesUnderTheRootItWatches() {
        var manager = new FileSystemWatcherManager(project);
        var glob = new FileSystemWatcher(Either.forLeft(directory.resolve("base") + "/../outside/*.txt"));
        manager.registerFileSystemWatchers("dots", List.of(glob));
        assertEquals(Set.of(new FileSystemWatchRoots.Root(directory.resolve("outside"), false)),
                FileSystemWatcherManager.externalRoots(List.of(glob), project));
        assertTrue(manager.isMatchFilePattern(directory.resolve("outside/data.txt").toUri(), WatchKind.Create));
    }

    public void testExplicitRelativeAndAbsoluteGlobsSelectEquivalentRoots() {
        Path path = directory.resolve("external");
        var expected = Set.of(new FileSystemWatchRoots.Root(path, true));
        assertEquals(expected, FileSystemWatcherManager.externalRoots(List.of(relative(path, "**/*.txt")), project));
        assertEquals(expected, FileSystemWatcherManager.externalRoots(List.of(
                new FileSystemWatcher(Either.forLeft(path + "/**/*.txt"))), project));
    }

    public void testFlatRelativePatternDoesNotScanAncestorRecursively() {
        var expected = Set.of(new FileSystemWatchRoots.Root(directory.resolve("external"), false));
        assertEquals(expected, FileSystemWatcherManager.externalRoots(
                List.of(relative(directory, "external/*.txt")), project));
    }

    public void testLiteralFileWatchesItsParentWithoutRecursion() {
        var expected = Set.of(new FileSystemWatchRoots.Root(directory.resolve("external"), false));
        assertEquals(expected, FileSystemWatcherManager.externalRoots(
                List.of(relative(directory, "external/config.json")), project));
    }

    public void testPureGlobsStillMatchWithoutPollingTheWorkspaceOrFilesystemRoot() {
        var manager = new FileSystemWatcherManager(project);
        var glob = new FileSystemWatcher(Either.forLeft("**/*.txt"));
        manager.registerFileSystemWatchers("pure", List.of(glob));
        assertTrue(manager.isMatchFilePattern(project.resolve("a/data.txt").toUri(), WatchKind.Create));
        assertTrue(FileSystemWatcherManager.externalRoots(List.of(glob), project).isEmpty());
        assertTrue(FileSystemWatcherManager.externalRoots(List.of(relative(directory.getRoot(), "**/*")), project).isEmpty());
    }

    public void testRootsInsideTheProjectAreNotWatched() {
        // the project's own VFS events cover them
        assertTrue(FileSystemWatcherManager.externalRoots(List.of(relative(project.resolve("src"), "**/*.txt"),
                new FileSystemWatcher(Either.forLeft(project.resolve("build") + "/**/*.class"))), project).isEmpty());
    }

    private void refresh() {
        var future = roots.refresh();
        PlatformTestUtil.waitWithEventsDispatching("scoped VFS refresh", future::isDone, 10);
        future.join();
    }

    private void assertEvent(Path path, Class<? extends VFileEvent> type) {
        assertTrue("Expected " + type.getSimpleName() + " for " + path + "; got " + events,
                events.stream().anyMatch(event -> type.isInstance(event) && path.equals(Path.of(event.getPath()))));
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    private static FileSystemWatcher relative(Path path, String pattern) {
        return new FileSystemWatcher(Either.forRight(new RelativePattern(Either.forRight(path.toUri().toString()), pattern)));
    }
}

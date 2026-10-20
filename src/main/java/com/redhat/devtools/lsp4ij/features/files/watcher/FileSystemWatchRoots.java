/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.files.watcher;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches and refreshes the roots outside the project that file system watchers name, so that the VFS reports the
 * changes there. IntelliJ watches only the project's content roots and the roots requested from
 * {@link LocalFileSystem}, reports changes only for the directories it has loaded, and does not refresh them while
 * the IDE stays focused.
 * <p>
 * Each root is watched natively, and its directories are loaded and refreshed every 2 seconds while it is
 * registered. A root that does not exist yet is observed through its nearest existing parent, flat, until it appears.
 * Symbolic links are not followed.
 */
final class FileSystemWatchRoots implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemWatchRoots.class);
    private final LocalFileSystem files = LocalFileSystem.getInstance();
    private Set<Root> roots = Set.of();
    private Set<Root> installedRoots = Set.of();
    private Set<Root> refreshingRoots = Set.of();
    private Set<LocalFileSystem.WatchRequest> requests = Set.of();
    private ScheduledFuture<?> timer;
    private CompletableFuture<Void> refresh = CompletableFuture.completedFuture(null);
    private boolean disposed;

    /**
     * A watched directory, with its subdirectories when recursive.
     */
    record Root(Path path, boolean recursive) { }

    synchronized void replace(Set<Root> roots) {
        if (disposed || this.roots.equals(roots)) {
            return;
        }
        this.roots = Set.copyOf(roots);
        releaseWatches();
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
        if (!roots.isEmpty()) {
            timer = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                    () -> refresh().exceptionally(error -> {
                        LOGGER.warn("Cannot refresh language server file watch roots", error);
                        return null;
                    }), 0, 2, TimeUnit.SECONDS);
        }
    }

    synchronized CompletableFuture<Void> refresh() {
        if (disposed || roots.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!refresh.isDone()) {
            if (refreshingRoots == roots) {
                return refresh;
            }
            // Wait for the old pass, then refresh the new registration generation.
            // Joining only the old future would acknowledge roots it never loaded.
            return refresh.handle((ignored, error) -> null).thenCompose(ignored -> refresh());
        }
        Set<Root> snapshot = roots;
        refreshingRoots = snapshot;
        var result = new CompletableFuture<Void>();
        refresh = result;
        AppExecutorUtil.getAppExecutorService().execute(() -> prepareRefresh(snapshot, result));
        return result;
    }

    private void prepareRefresh(Set<Root> snapshot, CompletableFuture<Void> result) {
        try {
            Set<Root> watches = new HashSet<>();
            Set<VirtualFile> refreshFiles = new LinkedHashSet<>();
            for (Root root : snapshot) {
                if (containsSymlink(root.path())) {
                    continue;
                }
                // An absent root is observed via its nearest existing ancestor, always flat.
                // The next pass descends toward the requested root as directories appear.
                Path existing = root.path();
                while (existing != null && !Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS)) {
                    existing = existing.getParent();
                }
                if (existing == null || existing.getParent() == null) {
                    continue;
                }
                watches.add(root);
                if (!existing.equals(root.path())) {
                    watches.add(new Root(existing, false));
                }
                // Observe deletion/recreation of an existing root as well as its contents.
                if (existing.equals(root.path()) && existing.getParent().getParent() != null) {
                    watches.add(new Root(existing.getParent(), false));
                }
                VirtualFile file = files.refreshAndFindFileByNioFile(existing);
                if (file != null) {
                    boolean recursive = root.recursive() && existing.equals(root.path());
                    collectDirectories(file, recursive, refreshFiles);
                }
            }
            synchronized (this) {
                if (disposed || roots != snapshot) {
                    result.complete(null);
                    return;
                }
                if (!installedRoots.equals(watches)) {
                    var recursive = watches.stream().filter(Root::recursive).map(root -> root.path().toString()).toList();
                    var flat = watches.stream().filter(root -> !root.recursive()).map(root -> root.path().toString()).toList();
                    requests = files.replaceWatchedRoots(requests, recursive, flat);
                    installedRoots = Set.copyOf(watches);
                }
            }
            // Refresh only enumerated real directories. Recursive VFS refresh could follow
            // symlinks or descend into an unrelated ancestor of a missing root.
            if (refreshFiles.isEmpty()) {
                result.complete(null);
            } else {
                files.refreshFiles(refreshFiles, true, false, () -> result.complete(null));
            }
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private static boolean containsSymlink(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static void collectDirectories(VirtualFile root, boolean recursive, Set<VirtualFile> result) {
        var pending = new ArrayDeque<VirtualFile>();
        pending.add(root);
        while (!pending.isEmpty()) {
            VirtualFile file = pending.removeFirst();
            // One cancellable read per directory, so a large tree never holds off write actions
            // for long. Listing a directory again after a cancelled read changes nothing.
            List<VirtualFile> subdirectories = ReadAction
                    .nonBlocking(() -> collectDirectory(file, result))
                    .executeSynchronously();
            if (recursive) {
                pending.addAll(subdirectories);
            }
        }
    }

    private static List<VirtualFile> collectDirectory(VirtualFile file, Set<VirtualFile> result) {
        if (!file.isValid() || !file.isDirectory() || file.is(VFileProperty.SYMLINK)) {
            return List.of();
        }
        result.add(file);
        // Loading children lets VFS report creations in previously unknown external roots.
        List<VirtualFile> children = List.of(file.getChildren());
        if (file instanceof NewVirtualFile local) {
            local.markDirty();
        }
        for (VirtualFile child : children) {
            if (!child.isDirectory() && !child.is(VFileProperty.SYMLINK) && child instanceof NewVirtualFile local) {
                local.markDirty();
            }
        }
        return children.stream().filter(VirtualFile::isDirectory).toList();
    }

    @TestOnly
    synchronized int watchRequestCount() {
        return requests.size();
    }

    private void releaseWatches() {
        files.removeWatchedRoots(requests);
        requests = Set.of();
        installedRoots = Set.of();
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        roots = Set.of();
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
        releaseWatches();
    }
}

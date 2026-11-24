/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.templates;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Refreshes the JAR which holds the LSP and DAP templates in the VFS, once per IDE start and only in a development
 * build, so that {@link ServerTemplateManager} reads the templates of a JAR rebuilt at the same path, not the ones of
 * the old JAR which the VFS still caches.
 * <p>
 * {@link ServerTemplateManager#getTemplateRoot()} reads the templates through {@link JarFileSystem}, whose files and
 * their content come from the VFS cache, which the IDE keeps from one start to the next. Only a refresh compares them
 * with the JAR, and nothing refreshes the JAR of a plugin, since the class loader of the plugin reads it directly, not
 * through the VFS. A released LSP4IJ has its version in the name of its JAR, so a new version is read from a new path;
 * only a development build, as in {@code runIde}, is rebuilt at the same path.
 * <p>
 * Whoever replaces the JAR restarts the IDE or reloads the plugin. Both run this activity again: a reloaded plugin has
 * a new class loader, so a new {@link #REFRESHED} flag, and the IDE runs the project activities of a plugin loaded
 * dynamically for the projects which are already open. A project activity runs in a background coroutine without the
 * read lock, where a synchronous refresh is allowed. {@link ServerTemplateManager#getTemplateRoot()} runs in the
 * constructor of the template services, which may run under the read lock, where it is not, so it never refreshes.
 * <p>
 * A template service created before this refresh keeps the old templates until the next start, as before. The
 * templates are only needed in the New Language Server dialog and in the DAP run configuration, after a user action,
 * so the refresh has long finished by then. It needs no index, so it runs without waiting for indexing to end.
 */
@ApiStatus.Internal
public class TemplateJarRefreshActivity implements ProjectActivity, DumbAware {

    /**
     * Set by the first refresh, so that the JAR is refreshed once per IDE start, not once per opened project.
     */
    private static final AtomicBoolean REFRESHED = new AtomicBoolean();

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        refreshOnce(IntelliJPlatformUtils.isDevMode(), REFRESHED, TemplateJarRefreshActivity::getTemplateJar);
        return null;
    }

    /**
     * Refreshes the given JAR and its whole content in the VFS, synchronously, unless {@code devMode} is false or
     * {@code refreshed} is already set. Must not be called under the read lock.
     *
     * @param devMode   whether the IDE runs a development build of LSP4IJ
     * @param refreshed set by the first refresh, to refresh only once
     * @param jar       the {@code .jar} file to refresh, or null if there is none
     * @return true if it refreshed the JAR, false otherwise
     */
    @VisibleForTesting
    static boolean refreshOnce(boolean devMode,
                               @NotNull AtomicBoolean refreshed,
                               @NotNull Supplier<@Nullable Path> jar) {
        if (!devMode || !refreshed.compareAndSet(false, true)) {
            return false;
        }
        Path jarPath = jar.get();
        VirtualFile localJar = jarPath != null ? LocalFileSystem.getInstance().findFileByNioFile(jarPath) : null;
        VirtualFile jarRoot = localJar != null && !localJar.isDirectory()
                ? JarFileSystem.getInstance().getJarRootForLocalFile(localJar)
                : null;
        if (jarRoot == null) {
            return false;
        }
        // The content, recursively, since a refresh of the .jar file refreshes the content only of a JAR which the
        // IDE has opened since it started. And the .jar file in the same session: a change of the .jar file makes the
        // VFS refresh the content again, asynchronously. That refresh could scan the content before a second session,
        // which would then find nothing to refresh, and return before the changes are applied.
        VfsUtil.markDirtyAndRefresh(false, true, true, localJar, jarRoot);
        return true;
    }

    /**
     * Returns the {@code .jar} file from which {@link ServerTemplateManager#getTemplateRoot()} reads the templates.
     */
    private static @Nullable Path getTemplateJar() {
        String resourceRoot = PathManager.getResourceRoot(ServerTemplateManager.class, "/templates");
        return resourceRoot != null ? Path.of(resourceRoot) : null;
    }
}

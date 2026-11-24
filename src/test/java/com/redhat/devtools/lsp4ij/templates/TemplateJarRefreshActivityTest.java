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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link TemplateJarRefreshActivity} tests: in a development build, it refreshes a JAR rebuilt at the same path, once,
 * so that the templates are read from the new JAR, not from what the VFS has cached of the old one. And
 * {@link ServerTemplateManager#getTemplateRoot()} never refreshes.
 * <p>
 * A test builds a JAR in a temporary directory, reads it through {@link JarFileSystem}, as LSP4IJ reads its
 * templates, then rebuilds it at the same path, as {@code runIde} does, and starts the IDE again.
 */
public class TemplateJarRefreshActivityTest extends BasePlatformTestCase {

    private Path directory;
    private Path jar;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        directory = Files.createTempDirectory("lsp4ij-templates-").toRealPath();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.toString());
        jar = directory.resolve("lsp4ij.jar");
    }

    public void testTheTemplatesOfARebuiltJarAreNewAfterTheRefresh() throws Exception {
        build(1, "typescript");
        assertTemplates(1, "typescript");
        build(2, "typescript", "rust");
        restartTheIde();
        assertTemplates(1, "typescript");

        assertTrue(refreshOnce(true, new AtomicBoolean()));

        assertTemplates(2, "rust", "typescript");
    }

    public void testItRefreshesOnlyOnceForAllProjects() throws Exception {
        build(1, "typescript");
        assertTemplates(1, "typescript");
        build(2, "typescript");
        restartTheIde();
        var refreshed = new AtomicBoolean();
        assertTrue("the first project must refresh the JAR", refreshOnce(true, refreshed));
        assertTemplates(2, "typescript");
        build(3, "typescript");
        restartTheIde();

        assertFalse("the next project must not refresh it again", refreshOnce(true, refreshed));

        assertTemplates(2, "typescript");
    }

    public void testItDoesNothingOutsideADevelopmentBuild() throws Exception {
        build(1, "typescript");
        assertTemplates(1, "typescript");
        build(2, "typescript");
        restartTheIde();

        assertFalse(refreshOnce(false, new AtomicBoolean()));

        assertTemplates(1, "typescript");
    }

    public void testGetTemplateRootNeverRefreshes() throws Exception {
        // The bundled templates are read from the jar of LSP4IJ in the test sandbox, which is outside the roots that a
        // test may read when the checkout isn't in the user's home directory
        String lsp4ijJar = PathManager.getResourceRoot(LanguageServerTemplateManager.class, "/templates/lsp");
        assertNotNull(lsp4ijJar);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), lsp4ijJar);
        var templateRoot = (NewVirtualFile) LanguageServerTemplateManager.getInstance().getTemplateRoot();
        assertNotNull("the LSP templates of the LSP4IJ jar", templateRoot);
        var jarRoot = (NewVirtualFile) JarFileSystem.getInstance().getRootByEntry(templateRoot);
        assertNotNull(jarRoot);
        jarRoot.markDirtyRecursively();

        // Created under the read lock, as a template service may be, where a synchronous refresh fails
        var templates = ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() ->
                new ServerTemplateManager<LanguageServerTemplate>("templates/lsp") {
                    @Override
                    public LanguageServerTemplate importServerTemplate(@NotNull VirtualFile templateFolder)
                            throws IOException {
                        return LanguageServerTemplateManager.getInstance().importServerTemplate(templateFolder);
                    }
                }.getTemplates()));
        assertNotEmpty(PlatformTestUtil.waitForFuture(templates, 30_000));
        waitForTheQueuedRefreshes();

        // A refresh, also an asynchronous one, would have cleared them
        assertTrue(jarRoot.isDirty());
        assertTrue(templateRoot.isDirty());
    }

    private boolean refreshOnce(boolean devMode, AtomicBoolean refreshed) {
        // In a background thread without the read lock, as a project activity runs
        var refresh = ApplicationManager.getApplication().executeOnPooledThread(() ->
                TemplateJarRefreshActivity.refreshOnce(devMode, refreshed, () -> jar));
        return PlatformTestUtil.waitForFuture(refresh, 30_000);
    }

    /**
     * Writes the JAR with a template.json in each of the given template directories, and with a modification time of
     * its own, as each build of a JAR has.
     */
    private void build(int build, String... templates) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("templates/"));
            zip.putNextEntry(new ZipEntry("templates/lsp/"));
            for (String template : templates) {
                zip.putNextEntry(new ZipEntry("templates/lsp/" + template + "/"));
                zip.putNextEntry(new ZipEntry("templates/lsp/" + template + "/template.json"));
                zip.write(templateJson(build, template).getBytes(StandardCharsets.UTF_8));
            }
        }
        Files.setLastModifiedTime(jar, FileTime.fromMillis(1_700_000_000_000L + build * 60_000L));
    }

    /**
     * Closes the JAR, which a new start of the IDE hasn't opened yet. The VFS keeps what it has cached of the JAR, as
     * it does from one start to the next.
     */
    private void restartTheIde() {
        VfsImplUtil.releaseHandler(FileUtil.toSystemIndependentName(jar.toString()));
    }

    private void assertTemplates(int build, String... templates) throws IOException {
        VirtualFile lsp = JarFileSystem.getInstance().findFileByPath(
                FileUtil.toSystemIndependentName(jar.toString()) + JarFileSystem.JAR_SEPARATOR + "templates/lsp");
        assertNotNull(lsp);
        assertSameElements(Arrays.stream(lsp.getChildren()).map(VirtualFile::getName).toList(), templates);
        for (String template : templates) {
            VirtualFile templateJson = lsp.findFileByRelativePath(template + "/template.json");
            assertNotNull(templateJson);
            assertEquals(templateJson(build, template), VfsUtilCore.loadText(templateJson));
        }
    }

    private static @NotNull String templateJson(int build, @NotNull String template) {
        return "{\"name\": \"" + template + "\", \"build\": " + build + "}";
    }

    /**
     * Waits until the refreshes queued so far have scanned their files: the VFS scans one refresh at a time, in the
     * order they were queued.
     */
    private void waitForTheQueuedRefreshes() throws IOException {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(
                Files.writeString(directory.resolve("later.txt"), "later"));
        assertNotNull(file);
        var refresh = ApplicationManager.getApplication().executeOnPooledThread(() -> file.refresh(false, false));
        PlatformTestUtil.waitForFuture(refresh, 30_000);
    }
}

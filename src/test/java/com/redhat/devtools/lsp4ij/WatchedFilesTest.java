/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.google.gson.JsonObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The client tells the server of the changes to the files its workspace/didChangeWatchedFiles registrations watch,
 * also in a directory outside the project, and only while the server that registered them runs.
 */
public class WatchedFilesTest extends LSPCodeInsightFixtureTestCase {

    public WatchedFilesTest() {
        super("*.foo");
    }

    public void testAFileCreatedInAWatchedDirectoryOutsideTheProjectIsReported() throws Exception {
        connect(myFixture.configureByText("main.foo", ""));
        Path directory = Files.createTempDirectory("lsp4ij-watched-").toRealPath();
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
        // present before the registration, to tell when the client has loaded the directory
        Files.writeString(directory.resolve("present.txt"), "present");
        register("external", watcher(directory, "**/*.txt"));
        PlatformTestUtil.waitWithEventsDispatching("the client must load the watched directory",
                () -> isLoaded(directory, "present.txt"), 10);

        // written by another process, while the IDE keeps its focus
        Path created = Files.writeString(directory.resolve("created.txt"), "created");

        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the created file",
                () -> received(created, FileChangeType.Created), 10);
    }

    public void testTheWatchersOfAStoppedServerAreDropped() throws Exception {
        var file = myFixture.configureByText("main.foo", "");
        var wrapper = connect(file);
        register("project", watcher(file.getVirtualFile().getParent().toNioPath(), "**/*.txt"));
        var before = myFixture.addFileToProject("before.txt", "").getVirtualFile().toNioPath();
        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the created file",
                () -> received(before, FileChangeType.Created), 10);

        wrapper.restart();
        connect(file);
        // the restarted server has registered no watcher
        var after = myFixture.addFileToProject("after.txt", "").getVirtualFile().toNioPath();
        // sent after the file event, and received after it, since notifications keep their order
        wrapper.sendNotification(ls -> {
            ls.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(new JsonObject()));
            return ls;
        });
        PlatformTestUtil.waitWithEventsDispatching("the server must receive the later notification",
                () -> MockLanguageServer.INSTANCE.getNotifications().contains("didChangeConfiguration"), 10);
        assertFalse("the server must not be told of changes for a watcher of the stopped server",
                received(after, FileChangeType.Created));
    }

    private LanguageServerWrapper connect(PsiFile file) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
    }

    private static FileSystemWatcher watcher(Path directory, String pattern) {
        return new FileSystemWatcher(Either.forRight(new RelativePattern(Either.forRight(directory.toUri().toString()), pattern)));
    }

    private static void register(String id, FileSystemWatcher watcher) throws Exception {
        var registration = new Registration(id, "workspace/didChangeWatchedFiles",
                new DidChangeWatchedFilesRegistrationOptions(List.of(watcher)));
        MockLanguageServer.INSTANCE.getRemoteProxies().get(0)
                .registerCapability(new RegistrationParams(List.of(registration)))
                .get(30, TimeUnit.SECONDS);
    }

    private static boolean isLoaded(Path directory, String child) {
        var file = LocalFileSystem.getInstance().findFileByNioFile(directory);
        return file instanceof NewVirtualFile loaded && loaded.findChildIfCached(child) != null;
    }

    private static boolean received(Path path, FileChangeType type) {
        return MockLanguageServer.INSTANCE.getWorkspaceService().getWatchedFileEvents().stream()
                .anyMatch(event -> event.getType() == type && Path.of(URI.create(event.getUri())).equals(path));
    }
}

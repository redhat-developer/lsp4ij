/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.installation.ConsoleProvider;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationContext;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the {@link LanguageServerManager} with a language server definition whose workspace folder strategy spreads
 * the files over several instances: a directory named "a" to "e" is the root of the instance of its files, and a file
 * in any other directory has none.
 */
public class LanguageServerManagerInstancesTest extends LSPCodeInsightFixtureTestCase {

    private static final String ID = "manager-instances";
    private static final List<String> ROOTS = List.of("a", "b", "c", "d", "e");

    private InstancesDefinition definition;
    private LanguageServerManager manager;

    public LanguageServerManagerInstancesTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var project = myFixture.getProject();
        definition = new InstancesDefinition();
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.instances"), "plaintext")));
        manager = LanguageServerManager.getInstance(project);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (var server : definition.servers) {
                server.waitBeforeTearDown();
            }
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
        } finally {
            super.tearDown();
        }
    }

    public void testTheLanguageServerOfAFileIsTheInstanceOfItsRoot() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));

        assertEquals("a", a.getInstanceRoot().getName());
        assertEquals("b", b.getInstanceRoot().getName());
        assertEquals(ServerStatus.started, a.getServerStatus());
        assertEquals(ServerStatus.started, b.getServerStatus());
        assertSame(a, serverOf(file("a/two.instances")));
    }

    public void testAFileWithoutARootHasNoLanguageServer() throws Exception {
        assertNull(waitFor(manager.getLanguageServer(ID, file("other/one.instances").getVirtualFile())));
        assertEmpty(instances());
    }

    public void testTheLanguageServerOfAFileDoesNotConnectIt() throws Exception {
        var file = file("a/one.instances");

        var a = serverOf(file);

        assertFalse(a.isConnectedTo(a.toUri(file.getVirtualFile())));
    }

    public void testTheLanguageServerOfTheIdIsTheOldestInstance() throws Exception {
        var instances = allStoppedInstances();

        var server = waitFor(manager.getLanguageServer(ID));

        assertNotNull(server);
        assertSame(instances.get(0), server.getServerWrapper());
        assertEquals(ServerStatus.started, instances.get(0).getServerStatus());
        for (var other : instances.subList(1, instances.size())) {
            assertEquals(ServerStatus.stopped, other.getServerStatus());
        }
    }

    public void testTheStatusOfTheIdIsThatOfTheOldestInstance() throws Exception {
        List<LanguageServerWrapper> instances = new ArrayList<>();
        for (var root : ROOTS) {
            instances.add(serverOf(file(root + "/one.instances")));
        }

        stop(instances.get(0));

        assertEquals(ServerStatus.stopped, manager.getServerStatus(ID));
    }

    public void testTheLanguageServersOfTheIdAreAllInstancesTheOldestFirst() throws Exception {
        var instances = allStoppedInstances();

        var servers = waitFor(manager.getLanguageServers(ID));

        assertEquals(instances, servers.stream().map(LanguageServerItem::getServerWrapper).toList());
        for (var instance : instances) {
            assertEquals(ServerStatus.started, instance.getServerStatus());
        }
    }

    public void testStartWithARootStartsOnlyThatInstance() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        stop(a);
        stop(b);

        manager.start(ID, a.getInstanceRoot(), new LanguageServerManager.StartOptions());

        waitForStatus(a, ServerStatus.started);
        assertEquals(ServerStatus.stopped, b.getServerStatus());
    }

    public void testStartWithARootAndForceStartCreatesItsInstance() throws Exception {
        var root = rootOf("a");

        manager.start(ID, root, new LanguageServerManager.StartOptions().setForceStart(true));

        var instances = instances();
        assertEquals(1, instances.size());
        assertEquals(root.getUri(), instances.get(0).getInstanceRoot().getUri());
        waitForStatus(instances.get(0), ServerStatus.started);
    }

    public void testStartWithARootWithoutForceStartCreatesNoInstance() throws Exception {
        manager.start(ID, rootOf("a"), new LanguageServerManager.StartOptions());
        dispatchAll();

        assertEmpty(instances());
    }

    public void testForceStartWithoutARootStartsNoInstance() throws Exception {
        manager.start(ID, new LanguageServerManager.StartOptions().setForceStart(true));
        dispatchAll();

        assertEmpty(instances());
    }

    public void testStopWithARootStopsOnlyThatInstance() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));

        manager.stop(ID, a.getInstanceRoot(), LanguageServerManager.StopOptions.DEFAULT);

        waitForStatus(a, ServerStatus.stopped);
        assertEquals(ServerStatus.started, b.getServerStatus());
    }

    public void testTheStatusOfARootIsThatOfItsInstance() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        stop(b);

        assertEquals(ServerStatus.started, manager.getServerStatus(ID, a.getInstanceRoot()));
        assertEquals(ServerStatus.stopped, manager.getServerStatus(ID, b.getInstanceRoot()));
        assertEquals(ServerStatus.none, manager.getServerStatus(ID, rootOf("c")));
    }

    public void testInstallRunsTheInstallerOnceForAllInstances() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));

        manager.install(ID, new ServerInstallationContext());

        PlatformTestUtil.waitWithEventsDispatching("The installation must run, and the instances must start again",
                () -> definition.installations.get() > 0
                        && a.getServerStatus() == ServerStatus.started && b.getServerStatus() == ServerStatus.started, 10);
        dispatchAll();
        assertEquals(1, definition.installations.get());
    }

    public void testInstallWithoutAnInstanceLeavesNoLanguageServer() throws Exception {
        manager.install(ID, new ServerInstallationContext());

        PlatformTestUtil.waitWithEventsDispatching("The installation must run",
                () -> definition.installations.get() > 0, 10);
        PlatformTestUtil.waitWithEventsDispatching("No language server may stay for the installation",
                () -> instances().isEmpty(), 10);
        assertEquals(1, definition.installations.get());
    }

    /**
     * Creates an instance for each root, the one of "a" first, and stops them.
     */
    private @NotNull List<LanguageServerWrapper> allStoppedInstances() throws Exception {
        List<LanguageServerWrapper> instances = new ArrayList<>();
        for (var root : ROOTS) {
            instances.add(serverOf(file(root + "/one.instances")));
        }
        for (var instance : instances) {
            stop(instance);
        }
        return instances;
    }

    private @NotNull PsiFile file(@NotNull String path) {
        return myFixture.addFileToProject(path, "text");
    }

    /**
     * Returns the root that the strategy of the tests gives the files of the given directory.
     */
    private @NotNull WorkspaceFolder rootOf(@NotNull String path) throws IOException {
        var directory = myFixture.getTempDirFixture().findOrCreateDir(path);
        return new WorkspaceFolder(FileUriSupport.toString(directory, FileUriSupport.DEFAULT), directory.getName());
    }

    private @NotNull LanguageServerWrapper serverOf(@NotNull PsiFile file) throws Exception {
        var server = waitFor(manager.getLanguageServer(ID, file.getVirtualFile()));
        assertNotNull(server);
        return server.getServerWrapper();
    }

    private static void stop(@NotNull LanguageServerWrapper instance) {
        instance.stop();
        waitForStatus(instance, ServerStatus.stopped);
    }

    private static void waitForStatus(@NotNull LanguageServerWrapper instance, @NotNull ServerStatus status) {
        PlatformTestUtil.waitWithEventsDispatching("The language server must be " + status,
                () -> instance.getServerStatus() == status, 10);
    }

    private static void dispatchAll() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private static <T> T waitFor(@NotNull CompletableFuture<T> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", future::isDone, 10);
        return future.get();
    }

    private @NotNull List<LanguageServerWrapper> instances() {
        return LanguageServiceAccessor.getInstance(myFixture.getProject()).getStartedServers()
                .stream()
                .filter(server -> server.getServerDefinition() == definition)
                .toList();
    }

    /**
     * Gives a file the directory it is in as the root of its instance, if that directory is named "a" to "e".
     */
    private static final class InstancesStrategy implements WorkspaceFolderStrategy {

        @Override
        public boolean hasServerInstances() {
            return true;
        }

        @Override
        public boolean sendAllFoldersOnInitialization() {
            return true;
        }

        @Override
        public @NotNull List<WorkspaceFolder> getWorkspaceFolders(@NotNull Project project,
                                                                  @NotNull FileUriSupport fileUriSupport) {
            return List.of();
        }

        @Override
        public @Nullable WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                                                   @NotNull Project project,
                                                                   @NotNull FileUriSupport fileUriSupport) {
            var directory = file.isDirectory() ? file : file.getParent();
            if (directory == null || !ROOTS.contains(directory.getName())) {
                return null;
            }
            return new WorkspaceFolder(FileUriSupport.toString(directory, FileUriSupport.DEFAULT), directory.getName());
        }
    }

    private static final class CountingInstaller implements ServerInstaller {

        private final AtomicInteger installations;

        CountingInstaller(@NotNull AtomicInteger installations) {
            this.installations = installations;
        }

        @Override
        public CompletableFuture<ServerInstallationStatus> checkInstallation() {
            return CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
        }

        @Override
        public @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation(@NotNull ServerInstallationContext context) {
            installations.incrementAndGet();
            return CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
        }

        @Override
        public ServerInstallationStatus getStatus() {
            return ServerInstallationStatus.INSTALLED;
        }

        @Override
        public void reset() {
        }

        @Override
        public void registerConsoleProvider(@NotNull ConsoleProvider consoleProvider) {
        }

        @Override
        public void unregisterConsoleProvider(@NotNull ConsoleProvider consoleProvider) {
        }
    }

    private static final class InstancesDefinition extends MockLanguageServerDefinition {

        final List<MockLanguageServer> servers = new CopyOnWriteArrayList<>();
        final AtomicInteger installations = new AtomicInteger();

        InstancesDefinition() {
            super(ID, false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var server = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
            servers.add(server);
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            var features = super.createClientFeatures().setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override
                protected @NotNull WorkspaceFolderStrategy createStrategy() {
                    return new InstancesStrategy();
                }
            });
            features.setServerInstaller(new CountingInstaller(installations));
            return features;
        }
    }
}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticsPassFactory;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.installation.ConsoleProvider;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationContext;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.LanguageServerException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tests the {@link LanguageServerManager} with a language server definition whose workspace folder strategy spreads
 * the files over several instances: a directory named "a" to "e" is the root of the instance of its files, and a file
 * in any other directory has none.
 */
public class LanguageServerManagerInstancesTest extends LSPCodeInsightFixtureTestCase {

    // Only ends a hang: a wait whose condition is met returns at once
    private static final int TIMEOUT = 60;
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
                        && a.getServerStatus() == ServerStatus.started && b.getServerStatus() == ServerStatus.started, TIMEOUT);
        dispatchAll();
        assertEquals(1, definition.installations.get());
    }

    public void testInstallWithoutAnInstanceLeavesNoLanguageServer() throws Exception {
        List<LanguageServerWrapper> disposed = new CopyOnWriteArrayList<>();
        var lifecycleManager = LanguageServerLifecycleManager.getInstance(myFixture.getProject());
        var listener = new LanguageServerLifecycleListener() {
            @Override
            public void handleStatusChanged(@NotNull LanguageServerWrapper languageServer) {
            }

            @Override
            public void handleLSPMessage(@NotNull Message message,
                                         @NotNull MessageConsumer messageConsumer,
                                         @NotNull LanguageServerWrapper languageServer) {
            }

            @Override
            public void handleError(@NotNull LanguageServerWrapper languageServer, @Nullable Throwable exception) {
            }

            @Override
            public void handleDisposed(@NotNull LanguageServerWrapper languageServer) {
                if (languageServer.getServerDefinition() == definition) {
                    disposed.add(languageServer);
                }
            }

            @Override
            public void dispose() {
            }
        };
        lifecycleManager.addLanguageServerLifecycleListener(listener);
        try {
            manager.install(ID, new ServerInstallationContext());

            // It is never one of the started language servers, so it has to be its dispose that says it went
            PlatformTestUtil.waitWithEventsDispatching("The language server of the installation must be disposed",
                    () -> definition.installations.get() > 0 && !disposed.isEmpty(), TIMEOUT);
            assertEquals(1, definition.installations.get());
            assertEquals(1, disposed.size());
            assertNull(disposed.get(0).getInstanceRoot());
            assertEmpty(instances());
        } finally {
            lifecycleManager.removeLanguageServerLifecycleListener(listener);
        }
    }

    public void testTheInstancesStoppedForAFailedInstallationStartAgain() throws Exception {
        serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        definition.failingInstallation = true;

        manager.install(ID, new ServerInstallationContext());

        // b is stopped before the installation runs, so started again once it failed. The instance that installs
        // stays stopped, as a language server without instances does
        PlatformTestUtil.waitWithEventsDispatching("The other instance must start again after the failed installation",
                () -> definition.installations.get() > 0 && b.getServerStatus() == ServerStatus.started, TIMEOUT);
        assertEquals(1, definition.installations.get());
    }

    public void testAnInstanceSwitchedOffDuringAnInstallationStaysOff() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        var gate = new CompletableFuture<Void>();
        definition.installationGate = gate;
        manager.install(ID, new ServerInstallationContext());
        // b is stopped before the installation runs, which waits for the gate
        PlatformTestUtil.waitWithEventsDispatching("The installation must run",
                () -> definition.installations.get() > 0, TIMEOUT);
        manager.stop(ID, b.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(b, ServerStatus.stopped);

        gate.complete(null);

        // a installs, and starts again before the instances stopped for the installation do
        waitForStatus(a, ServerStatus.started);
        assertFalse(b.isEnabled());
        assertEquals(ServerStatus.stopped, b.getServerStatus());
    }

    public void testTheRootOfAnInstanceCanBeWrittenInAnotherWay() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var root = a.getInstanceRoot();
        var uri = root.getUri();
        var otherWay = new WorkspaceFolder(uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri + "/", root.getName());

        assertEquals(ServerStatus.started, manager.getServerStatus(ID, otherWay));
        manager.stop(ID, otherWay, new LanguageServerManager.StopOptions().setWillDisable(false));
        waitForStatus(a, ServerStatus.stopped);
        manager.start(ID, otherWay, new LanguageServerManager.StartOptions().setForceStart(true));
        waitForStatus(a, ServerStatus.started);
        assertEquals(List.of(a), instances());
    }

    public void testAStopWithARootSwitchesOffOnlyThatInstance() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));

        manager.stop(ID, a.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(a, ServerStatus.stopped);

        assertFalse(a.isEnabled());
        assertTrue(b.isEnabled());
        assertEquals(ServerStatus.started, b.getServerStatus());
        assertTrue(definition.isEnabled(myFixture.getProject()));
        assertNull(waitFor(manager.getLanguageServer(ID, file("a/two.instances").getVirtualFile())));
    }

    public void testAStartWithTheRootOfASwitchedOffInstanceNeedsWillEnable() throws Exception {
        var a = serverOf(file("a/one.instances"));
        manager.stop(ID, a.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(a, ServerStatus.stopped);

        assertThrows(LanguageServerException.class,
                () -> manager.start(ID, a.getInstanceRoot(), new LanguageServerManager.StartOptions().setWillEnable(false)));
        assertFalse(a.isEnabled());

        manager.start(ID, a.getInstanceRoot(), new LanguageServerManager.StartOptions().setWillEnable(true));
        waitForStatus(a, ServerStatus.started);
        assertTrue(a.isEnabled());
    }

    public void testAStartWithoutARootLeavesASwitchedOffInstanceOff() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        manager.stop(ID, a.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(a, ServerStatus.stopped);
        stop(b);

        manager.start(ID, new LanguageServerManager.StartOptions().setWillEnable(false));

        waitForStatus(b, ServerStatus.started);
        assertFalse(a.isEnabled());
        assertEquals(ServerStatus.stopped, a.getServerStatus());

        manager.start(ID, new LanguageServerManager.StartOptions().setWillEnable(true));
        waitForStatus(a, ServerStatus.started);
        assertTrue(a.isEnabled());
    }

    public void testAStopOfTheDefinitionKeepsTheInstancesOfOtherRootsOff() throws Exception {
        var a = serverOf(file("a/one.instances"));

        manager.stop(ID, new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(a, ServerStatus.stopped);

        assertFalse(definition.isEnabled(myFixture.getProject()));
        assertNull(waitFor(manager.getLanguageServer(ID, file("c/one.instances").getVirtualFile())));
        assertTrue(instances().stream().noneMatch(instance -> instance.getServerStatus() == ServerStatus.started));
    }

    public void testAStartWithTheRootOfAnOpenFileWithoutInstanceSwitchesTheDefinitionOn() throws Exception {
        var a = serverOf(file("a/one.instances"));
        manager.stop(ID, new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(a, ServerStatus.stopped);
        // The definition is switched off, so the file opened now gets no instance
        myFixture.openFileInEditor(file("b/one.instances").getVirtualFile());
        dispatchAll();
        assertNull(instanceOfRoot("b"));

        manager.start(ID, rootOf("b"), new LanguageServerManager.StartOptions().setWillEnable(true));

        assertTrue(definition.isEnabled(myFixture.getProject()));
        PlatformTestUtil.waitWithEventsDispatching("The instance of the open file must start", () -> {
            var b = instanceOfRoot("b");
            return b != null && b.getServerStatus() == ServerStatus.started;
        }, TIMEOUT);
        // The instance of a is switched on with the definition, and starts as a file of it is needed
        assertEquals(ServerStatus.stopped, a.getServerStatus());
    }

    public void testSwitchingTheDefinitionOnAfterItsStopSwitchesItsInstancesOn() throws Exception {
        var a = serverOf(file("a/one.instances"));
        var b = serverOf(file("b/one.instances"));
        manager.stop(ID, b.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(b, ServerStatus.stopped);

        // As the settings do around an installation
        manager.stop(ID);
        waitForStatus(a, ServerStatus.stopped);
        assertFalse(a.isEnabled());
        definition.setEnabled(true, myFixture.getProject());

        assertTrue(a.isEnabled());
        assertSame(a, serverOf(file("a/two.instances")));
        assertEquals(ServerStatus.started, a.getServerStatus());
        // An instance that a stop with its root switched off stays off
        assertFalse(b.isEnabled());
    }

    public void testAMovedFileGoesToTheInstanceOfItsNewRoot() throws Exception {
        var file = file("a/moved.instances").getVirtualFile();
        var a = openAndConnect(file);

        move(file, myFixture.getTempDirFixture().findOrCreateDir("b"));

        var instance = waitForTheInstanceConnectedTo(file);
        assertEquals("b", instance.getInstanceRoot().getName());
        assertFalse(a.isConnectedTo(a.toUri(file)));
    }

    public void testAMovedFileWithoutARootIsConnectedToNoInstance() throws Exception {
        var file = file("a/moved.instances").getVirtualFile();
        var a = openAndConnect(file);

        var oldUri = a.toUri(file);

        move(file, myFixture.getTempDirFixture().findOrCreateDir("other"));

        // The move takes the file from its instance, and decides its new one in a read action that it submits
        PlatformTestUtil.waitWithEventsDispatching("The moved file must leave its instance",
                () -> !a.isConnectedTo(oldUri), TIMEOUT);
        dispatchAll();
        assertNull(instanceConnectedTo(file));
        assertFalse(a.isConnectedTo(a.toUri(file)));
        assertEquals(List.of(a), instances());
    }

    public void testAFileMovedToASwitchedOffInstanceLeavesItOff() throws Exception {
        var b = serverOf(file("b/one.instances"));
        manager.stop(ID, b.getInstanceRoot(), new LanguageServerManager.StopOptions().setWillDisable(true));
        waitForStatus(b, ServerStatus.stopped);
        var file = file("a/moved.instances").getVirtualFile();
        var a = openAndConnect(file);
        var oldUri = a.toUri(file);

        move(file, myFixture.getTempDirFixture().findOrCreateDir("b"));

        // The move takes the file from its instance, and decides its new one in a read action that it submits
        PlatformTestUtil.waitWithEventsDispatching("The moved file must leave its instance",
                () -> !a.isConnectedTo(oldUri), TIMEOUT);
        dispatchAll();
        assertFalse(b.isEnabled());
        assertEquals(ServerStatus.stopped, b.getServerStatus());
        assertNull(instanceConnectedTo(file));
    }

    public void testAFileMovedAgainBeforeItIsConnectedGoesToTheInstanceOfItsLastPlace() throws Exception {
        var file = file("a/moved.instances").getVirtualFile();
        var bDirectory = myFixture.getTempDirFixture().findOrCreateDir("b");
        var cDirectory = myFixture.getTempDirFixture().findOrCreateDir("c");
        openAndConnect(file);
        // The file moves again as the instance of b is chosen for it, in an event of the UI thread before the one in
        // which the choice connects the file: no instance has the file during that move, so none sees it
        definition.instanceRootAsked = asked -> {
            if (asked.equals(file) && bDirectory.equals(asked.getParent())) {
                definition.instanceRootAsked = null;
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> file.move(this, cDirectory));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, ModalityState.nonModal());
            }
        };

        move(file, bDirectory);

        PlatformTestUtil.waitWithEventsDispatching("The file must move again", () -> cDirectory.equals(file.getParent()), TIMEOUT);
        var instance = waitForTheInstanceConnectedTo(file);
        assertEquals("c", instance.getInstanceRoot().getName());
        var b = instanceOfRoot("b");
        assertNotNull(b);
        assertFalse(b.isConnectedTo(b.toUri(file)));
    }

    public void testAFileOfAMovedDirectoryGoesToTheInstanceOfItsNewPlace() throws Exception {
        var file = file("x/a/moved.instances").getVirtualFile();
        var old = openAndConnect(file);
        var oldRoot = old.getInstanceRoot().getUri();

        move(file.getParent(), myFixture.getTempDirFixture().findOrCreateDir("y"));

        var instance = waitForTheInstanceConnectedTo(file);
        assertNotSame(old, instance);
        assertFalse(oldRoot.equals(instance.getInstanceRoot().getUri()));
        assertEquals(rootOf("y/a").getUri(), instance.getInstanceRoot().getUri());
    }

    public void testAMovedFileShowsOnlyTheDiagnosticsOfItsNewInstance() throws Exception {
        // The pass factory shows the diagnostics: registered here as the project's registrar does, so that this test
        // doesn't depend on another one having highlighted a file before
        new LSPDiagnosticsPassFactory().registerHighlightingPassFactory(
                TextEditorHighlightingPassRegistrar.getInstance(myFixture.getProject()), myFixture.getProject());
        var file = file("a/moved.instances").getVirtualFile();
        openAndConnect(file);
        PlatformTestUtil.waitWithEventsDispatching("The diagnostics of instance a must be shown",
                () -> List.of("from a").equals(shownDiagnostics(file)), TIMEOUT);

        move(file, myFixture.getTempDirFixture().findOrCreateDir("b"));

        PlatformTestUtil.waitWithEventsDispatching("The diagnostics of instance b must be shown",
                () -> shownDiagnostics(file).contains("from b"), TIMEOUT);
        dispatchAll();
        assertEquals(List.of("from b"), shownDiagnostics(file));
    }

    public void testAFileMovedWithinItsRootStaysWithItsInstance() throws Exception {
        var file = file("a/moved.instances").getVirtualFile();
        var a = openAndConnect(file);

        EdtTestUtil.runInEdtAndWait(() -> WriteCommandAction.writeCommandAction(myFixture.getProject())
                .run(() -> file.rename(this, "renamed.instances")));

        assertSame(a, waitForTheInstanceConnectedTo(file));
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

    private @NotNull List<String> shownDiagnostics(@NotNull VirtualFile file) {
        var document = FileDocumentManager.getInstance().getDocument(file);
        assertNotNull(document);
        return DaemonCodeAnalyzerImpl.getHighlights(document, null, myFixture.getProject())
                .stream()
                .map(HighlightInfo::getDescription)
                .filter(Objects::nonNull)
                .toList();
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

    private @NotNull LanguageServerWrapper openAndConnect(@NotNull VirtualFile file) throws Exception {
        myFixture.openFileInEditor(file);
        var psiFile = myFixture.getPsiManager().findFile(file);
        assertNotNull(psiFile);
        var servers = waitFor(LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(psiFile, null, null));
        assertEquals(1, servers.size());
        var instance = servers.get(0).getServerWrapper();
        assertTrue(instance.isConnectedTo(instance.toUri(file)));
        return instance;
    }

    private void move(@NotNull VirtualFile file, @NotNull VirtualFile target) throws IOException {
        EdtTestUtil.runInEdtAndWait(() -> WriteCommandAction.writeCommandAction(myFixture.getProject())
                .run(() -> file.move(this, target)));
    }

    private @Nullable LanguageServerWrapper instanceConnectedTo(@NotNull VirtualFile file) {
        for (var instance : instances()) {
            if (instance.isConnectedTo(instance.toUri(file))) {
                return instance;
            }
        }
        return null;
    }

    private @NotNull LanguageServerWrapper waitForTheInstanceConnectedTo(@NotNull VirtualFile file) {
        PlatformTestUtil.waitWithEventsDispatching("The moved file must be connected to an instance",
                () -> instanceConnectedTo(file) != null, TIMEOUT);
        var instance = instanceConnectedTo(file);
        assertNotNull(instance);
        return instance;
    }

    private static void stop(@NotNull LanguageServerWrapper instance) {
        instance.stop();
        waitForStatus(instance, ServerStatus.stopped);
    }

    private static void waitForStatus(@NotNull LanguageServerWrapper instance, @NotNull ServerStatus status) {
        PlatformTestUtil.waitWithEventsDispatching("The language server must be " + status,
                () -> instance.getServerStatus() == status, TIMEOUT);
    }

    private static void dispatchAll() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private @Nullable LanguageServerWrapper instanceOfRoot(@NotNull String name) {
        for (var instance : instances()) {
            var root = instance.getInstanceRoot();
            if (root != null && name.equals(root.getName())) {
                return instance;
            }
        }
        return null;
    }

    private static <T> T waitFor(@NotNull CompletableFuture<T> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", future::isDone, TIMEOUT);
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

        private final @NotNull InstancesDefinition definition;

        InstancesStrategy(@NotNull InstancesDefinition definition) {
            this.definition = definition;
        }

        @Override
        public boolean hasServerInstances() {
            return true;
        }

        @Override
        public @Nullable WorkspaceFolder getServerInstanceRoot(@NotNull VirtualFile file,
                                                               @NotNull Project project,
                                                               @NotNull FileUriSupport fileUriSupport) {
            var asked = definition.instanceRootAsked;
            if (asked != null) {
                asked.accept(file);
            }
            return getWorkspaceFolderForFile(file, project, fileUriSupport);
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
        private final @NotNull BooleanSupplier failing;
        private final @NotNull Supplier<@Nullable CompletableFuture<Void>> gate;

        CountingInstaller(@NotNull AtomicInteger installations,
                          @NotNull BooleanSupplier failing,
                          @NotNull Supplier<@Nullable CompletableFuture<Void>> gate) {
            this.installations = installations;
            this.failing = failing;
            this.gate = gate;
        }

        @Override
        public CompletableFuture<ServerInstallationStatus> checkInstallation() {
            return CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
        }

        @Override
        public @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation(@NotNull ServerInstallationContext context) {
            installations.incrementAndGet();
            if (failing.getAsBoolean()) {
                return CompletableFuture.failedFuture(new IllegalStateException("The installation fails"));
            }
            // An installation that the test ends when it completes the gate
            var installed = gate.get();
            return installed != null
                    ? installed.thenApply(unused -> ServerInstallationStatus.INSTALLED)
                    : CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
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
        volatile boolean failingInstallation;
        volatile @Nullable CompletableFuture<Void> installationGate;
        // Is given each file whose instance root the strategy is asked for
        volatile @Nullable Consumer<VirtualFile> instanceRootAsked;

        InstancesDefinition() {
            super(ID, false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var server = new MockLanguageServer(InstancesDefinition::capabilities);
            servers.add(server);
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                          @NotNull WorkspaceFolder instanceRoot) {
            var provider = (MockConnectionProvider) createConnectionProvider(project);
            // Each instance publishes a diagnostic that names it
            servers.get(servers.size() - 1).getTextDocumentService().setPublishDiagnostics(List.of(
                    new Diagnostic(new Range(new Position(0, 0), new Position(0, 4)), "from " + instanceRoot.getName())));
            return provider;
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            var features = super.createClientFeatures().setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override
                protected @NotNull WorkspaceFolderStrategy createStrategy() {
                    return new InstancesStrategy(InstancesDefinition.this);
                }
            });
            features.setServerInstaller(new CountingInstaller(installations, () -> failingInstallation, () -> installationGate));
            return features;
        }

        /**
         * LSP4IJ handles a renamed or moved file only for a language server that supports workspace/didRenameFiles.
         */
        private static ServerCapabilities capabilities() {
            var operations = new FileOperationsServerCapabilities();
            operations.setDidRename(new FileOperationOptions(List.of(
                    new FileOperationFilter(new FileOperationPattern("**/*.instances")))));
            var workspace = new WorkspaceServerCapabilities();
            workspace.setFileOperations(operations);
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setWorkspace(workspace);
            return capabilities;
        }
    }
}

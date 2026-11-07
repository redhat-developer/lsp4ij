/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.documentContent.DocumentContentProvider;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.MarkersWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleListener;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.LanguageServerSettingsListener;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tests a language server definition whose workspace folder strategy spreads the files over several instances. By
 * default, each workspace folder has an instance. The folders of these tests are "a" and "b", and a file in any other
 * folder has none. {@link TwoLevelStrategy} separates the instances from the folders.
 */
public class LanguageServerInstancesTest extends LSPCodeInsightFixtureTestCase {

    // Only ends a hang: a wait whose condition is met returns at once
    private static final int TIMEOUT = 60;

    private PerFolderDefinition definition;
    private LanguageServiceAccessor accessor;

    public LanguageServerInstancesTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var project = myFixture.getProject();
        definition = new PerFolderDefinition();
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.per-folder"), "plaintext")));
        accessor = LanguageServiceAccessor.getInstance(project);
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

    public void testFilesOfOneFolderShareAnInstance() throws Exception {
        var one = instanceOf(file("a/one.per-folder"));
        var two = instanceOf(file("a/two.per-folder"));

        assertSame(one, two);
        assertEquals(1, instances().size());
        assertEquals(1, definition.servers.size());
    }

    public void testFilesOfTwoFoldersGetTwoInstances() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        assertNotSame(a, b);
        assertEquals("a", a.getInstanceRoot().getName());
        assertEquals("b", b.getInstanceRoot().getName());
        assertEquals(2, definition.servers.size());
    }

    public void testAFileWithoutAFolderGetsNoInstance() throws Exception {
        assertEmpty(serversOf(file("other/one.per-folder")));
        assertEmpty(instances());
    }

    public void testAConnectedFileStaysWithItsInstance() throws Exception {
        var file = file("a/one.per-folder");
        var instance = instanceOf(file);

        definition.strategy.noFolders = true;

        assertSame(instance, instanceOf(file));
        assertEmpty(serversOf(file("a/two.per-folder")));
    }

    public void testWithoutAFileAllStartedInstancesAreUsed() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        var servers = allServers();

        assertSameElements(wrappersOf(servers), a, b);
    }

    public void testWithoutAFileNoInstanceStarts() throws Exception {
        assertEmpty(allServers());
        assertEmpty(instances());
    }

    public void testStartedInstancesAreMatchedByFolder() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var otherFileOfA = file("a/two.per-folder");
        var fileOfB = file("b/one.per-folder");

        assertTrue(accessor.hasAny(otherFileOfA, server -> server == a));
        assertFalse(accessor.hasAny(fileOfB, server -> server.getServerDefinition() == definition));

        var processed = new ArrayList<LanguageServerWrapper>();
        accessor.processLanguageServers(otherFileOfA, processed::add);
        assertSameElements(processed, a);
        processed.clear();
        accessor.processLanguageServers(fileOfB, processed::add);
        assertEmpty(processed);
    }

    public void testAFileIsOnlyTrackedByTheInstanceOfItsFolder() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        var trackers = accessor.getModificationTrackers(file("a/two.per-folder"));

        assertTrue(trackers.contains(a.getModificationTracker()));
        assertFalse(trackers.contains(b.getModificationTracker()));
    }

    public void testAnInstanceCanStartWhileOtherThreadsGoThroughTheStartedServers() throws Exception {
        instanceOf(file("a/one.per-folder"));
        var startedServers = accessor.getStartedServers().iterator();

        instanceOf(file("b/one.per-folder"));

        assertNotNull(startedServers.next());
    }

    public void testAnInstanceThatTheFilterRejectsIsDisposed() throws Exception {
        var rejected = new ArrayList<LanguageServerWrapper>();

        var servers = waitFor(accessor.getLanguageServers(file("a/one.per-folder"), features -> {
            rejected.add(features.getServerWrapper());
            return false;
        }, null));

        assertEmpty(servers);
        assertEmpty(instances());
        assertEquals(1, rejected.size());
        assertTrue(rejected.get(0).isDisposed());
    }

    public void testAnInstanceThatTheFilterRejectsLaterStaysStarted() throws Exception {
        var file = file("a/one.per-folder");
        var instance = instanceOf(file);

        var servers = waitFor(accessor.getLanguageServers(file, features -> false, null));

        assertEmpty(servers);
        assertFalse(instance.isDisposed());
        assertSameElements(instances(), instance);
    }

    public void testAFailingStrategyDoesNotStopTheOtherLanguageServersOfAFile() throws Exception {
        var other = new MockLanguageServerDefinition("per-folder-other-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), other,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.per-folder"), "plaintext")));
        try {
            definition.strategy.failing = true;

            var servers = serversOf(file("a/one.per-folder"));

            assertEquals(1, servers.size());
            assertSame(other, servers.get(0).getServerWrapper().getServerDefinition());
            assertEmpty(instances());
        } finally {
            other.getServer().waitBeforeTearDown();
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), other);
        }
    }

    public void testAStrategyThatCannotBeCreatedKeepsOneInstance() {
        var failing = new MockLanguageServerDefinition("per-folder-failing-server", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                throw new IllegalStateException("No client features");
            }
        };

        assertFalse(failing.hasServerInstances());
    }

    public void testAChangedStrategyReplacesTheInstances() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));

        definition.strategy.perFolder = false;
        fireStrategyChanged();

        assertEmpty(instances());
        var replacement = instanceOf(file("a/two.per-folder"));
        assertNull(replacement.getInstanceRoot());
        waitUntilDisposed(instance);
    }

    public void testAReplacedInstanceGetsShutdown() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        var server = definition.servers.get(0);
        assertTrue(server.isRunning());

        fireStrategyChanged();
        waitUntilDisposed(instance);

        assertFalse(server.isRunning());
    }

    public void testAnInstanceThatFailsToStopAsItIsReplacedKeepsNoOtherInstanceRunning() throws Exception {
        definition.failingClientRoot = "a";
        definition.clientFailures.add(new IllegalStateException("The language client fails to be disposed"));
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));
        var serverOfB = definition.servers.get(1);
        assertTrue(serverOfB.isRunning());

        fireStrategyChanged();

        waitUntilDisposed(a);
        waitUntilDisposed(b);
        assertFalse(serverOfB.isRunning());
        assertEmpty(instances());
    }

    public void testACanceledDisposeOfAnInstanceThatFailedToStopLeavesTheOtherInstancesStarted() throws Exception {
        definition.failingClientRoot = "a";
        // The stop of the instance of a fails, and its dispose that follows is canceled
        definition.clientFailures.add(new IllegalStateException("The language client fails to be disposed"));
        definition.clientFeaturesFailures.add(new ProcessCanceledException());
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        // The registry logs what a listener throws, the cancellation too
        fireStrategyChanged();

        assertTrue(a.isDisposed());
        assertFalse(b.isDisposed());
        assertTrue("An instance that the canceled replacement did not reach must stay started, for a later change, "
                + "removal or close to dispose", instances().contains(b));
    }

    public void testAnInstanceWhoseStopIsCanceledAsItIsReplacedIsDisposed() throws Exception {
        definition.failingClientRoot = "a";
        definition.clientFailures.add(new ProcessCanceledException());
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));
        var childDisposed = new AtomicBoolean();
        Disposer.register(a, () -> childDisposed.set(true));

        // The registry logs what a listener throws, the cancellation too
        fireStrategyChanged();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue("An instance whose stop is canceled is no longer started, so it must be disposed", a.isDisposed());
        assertTrue("Its children, such as its file listener, must be disposed too", childDisposed.get());
        assertFalse(instances().contains(a));
        assertTrue(instances().contains(b));
    }

    public void testAnInstanceThatFailsToStopAsItIsReplacedIsDisposedThroughTheDisposer() throws Exception {
        definition.failingClientRoot = "a";
        definition.clientFailures.add(new IllegalStateException("The language client fails to be disposed"));
        var a = instanceOf(file("a/one.per-folder"));
        instanceOf(file("b/one.per-folder"));
        var childDisposed = new AtomicBoolean();
        Disposer.register(a, () -> childDisposed.set(true));

        fireStrategyChanged();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        assertTrue(a.isDisposed());
        assertTrue("The children of the instance, such as its file listener, must be disposed", childDisposed.get());
    }

    public void testANewInstanceThatAFailingFilterRefusesIsDisposed() throws Exception {
        List<LanguageServerWrapper> disposed = new CopyOnWriteArrayList<>();
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
        var lifecycleManager = LanguageServerLifecycleManager.getInstance(myFixture.getProject());
        lifecycleManager.addLanguageServerLifecycleListener(listener);
        try {
            var file = file("a/one.per-folder");

            // As a filter that is canceled does
            assertThrows(IllegalStateException.class, () -> accessor.getLanguageServers(file, clientFeatures -> {
                throw new IllegalStateException("The filter fails");
            }, null));

            assertEmpty(instances());
            assertEquals(1, disposed.size());
            assertEquals("a", disposed.get(0).getInstanceRoot().getName());
        } finally {
            lifecycleManager.removeLanguageServerLifecycleListener(listener);
        }
    }

    public void testARootChosenBeforeAReplacementGetsNoInstance() throws Exception {
        var file = file("x/a/one.per-folder");
        var twoLevels = new TwoLevelStrategy(file("base.txt").getVirtualFile().getParent(), true);
        var chosen = new CountDownLatch(1);
        var replaced = new CountDownLatch(1);
        definition.strategy.afterAsked = asked -> {
            if (asked.equals(file.getVirtualFile())) {
                definition.strategy.afterAsked = null;
                chosen.countDown();
                try {
                    assertTrue(replaced.await(TIMEOUT, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        // The request chooses the root of the file on its own thread, which the strategy holds then
        var servers = CompletableFuture.supplyAsync(
                        () -> ReadAction.compute(() -> accessor.getLanguageServers(file, null, null)),
                        AppExecutorUtil.getAppExecutorService())
                .thenCompose(future -> future);
        assertTrue(chosen.await(TIMEOUT, TimeUnit.SECONDS));

        // As a changed configuration does, which gives the file another root and replaces the instances
        definition.activeStrategy = twoLevels;
        definition.resetWorkspaceFolderStrategy();
        fireStrategyChanged();
        replaced.countDown();

        waitFor(servers);
        assertTrue("No instance may have the root chosen with the configuration before", instances().stream()
                .noneMatch(instance -> instance.getInstanceRoot() != null && "a".equals(instance.getInstanceRoot().getName())));
    }

    public void testAFileThatMovesAsItsInstanceIsChosenGetsNoInstanceOfItsEarlierPlace() throws Exception {
        var file = file("a/one.per-folder");
        var bDirectory = file("b/other.per-folder").getVirtualFile().getParent();
        definition.strategy.afterAsked = asked -> {
            if (asked.equals(file.getVirtualFile())) {
                definition.strategy.afterAsked = null;
                // Once the strategy has chosen the folder of the file, before the request takes its instance
                try {
                    move(asked, bDirectory);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

        assertEmpty(serversOf(file));
        assertEmpty("No instance may start for the earlier place of the file", instances());
    }

    public void testAFileThatMovesWhileItsInstanceStartsIsNotConnectedToIt() throws Exception {
        var file = file("a/one.per-folder");
        var bDirectory = file("b/other.per-folder").getVirtualFile().getParent();
        var initialize = new CompletableFuture<Void>();
        definition.initializeGate = initialize;
        var servers = accessor.getLanguageServers(file, null, null);
        var a = instances().get(0);
        assertEquals("a", a.getInstanceRoot().getName());

        // As an undo can do while the instance starts: the instance has no file yet, so it sees no move
        move(file.getVirtualFile(), bDirectory);
        initialize.complete(null);

        var result = waitFor(servers);
        assertFalse("The instance of the earlier place of the file must not get it",
                a.isConnectedTo(a.toUri(file.getVirtualFile())));
        assertEmpty(result);
    }

    public void testAFileThatMovesWhileItsConnectionWaitsIsNotConnectedToItsEarlierPlace() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var file = file("a/two.per-folder");
        var bDirectory = file("b/other.per-folder").getVirtualFile().getParent();
        Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        a.setConnectionExecutor(waiting::add);
        var earlierUri = a.toUri(file.getVirtualFile());
        var servers = accessor.getLanguageServers(file, null, null);
        PlatformTestUtil.waitWithEventsDispatching("The connection must wait", () -> !waiting.isEmpty(), TIMEOUT);

        // The instance has no document of the file yet, so it sees no move
        move(file.getVirtualFile(), bDirectory);
        a.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
        waiting.forEach(AppExecutorUtil.getAppExecutorService()::execute);

        var result = waitFor(servers);
        assertFalse("The instance must not get the file with the URI of its earlier place", a.isConnectedTo(earlierUri));
        assertEmpty(result);
    }

    public void testAFileThatMovesAsItsConnectionTakesItsUriIsNotConnectedToTheInstanceOfItsEarlierPlace()
            throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var file = file("a/two.per-folder");
        var bDirectory = file("b/other.per-folder").getVirtualFile().getParent();
        var armed = new AtomicBoolean();
        var moved = new AtomicBoolean();
        definition.uriAsked = asked -> {
            // The connection takes the URI of the file on its own thread, after the filter that arms this
            if (asked.equals(file.getVirtualFile()) && !ApplicationManager.getApplication().isDispatchThread()
                    && armed.compareAndSet(true, false)) {
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    try {
                        move(asked, bDirectory);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, ModalityState.nonModal());
                moved.set(true);
            }
        };

        // The filter runs just before the connection starts
        var servers = accessor.getLanguageServers(file, null, features -> {
            armed.set(true);
            return true;
        });

        var result = waitFor(servers);
        assertTrue("The file must move as its connection takes its URI", moved.get());
        assertFalse("The instance of the earlier place of the file must not get it",
                a.isConnectedTo(a.toUri(file.getVirtualFile())));
        assertEmpty(result);
    }

    public void testAReplacedInstanceIsDisposedOnTheUiThread() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        var disposedOnTheUiThread = new AtomicBoolean();
        Disposer.register(instance, () -> disposedOnTheUiThread.set(ApplicationManager.getApplication().isDispatchThread()));

        fireStrategyChanged();
        waitUntilDisposed(instance);

        assertTrue(disposedOnTheUiThread.get());
    }

    public void testAChangedStrategySwitchesTheDefinitionOnAgain() throws Exception {
        instanceOf(file("a/one.per-folder"));
        LanguageServerManager.getInstance(myFixture.getProject())
                .stop(definition.getId(), new LanguageServerManager.StopOptions().setWillDisable(true));
        assertFalse(definition.isEnabled(myFixture.getProject()));

        fireStrategyChanged();

        assertTrue(definition.isEnabled(myFixture.getProject()));
    }

    public void testAChangedStrategySwitchesAStoppedInstanceOnAgain() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        instance.stopAndDisable();
        assertFalse(instance.isEnabled());

        fireStrategyChanged();

        var replacement = instanceOf(file("a/two.per-folder"));
        assertNotSame(instance, replacement);
        assertTrue(replacement.isEnabled());
    }

    public void testAStopOfOneInstanceLeavesTheOthersWorking() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        a.stopAndDisable();

        assertFalse(a.isEnabled());
        assertTrue(b.isEnabled());
        assertTrue(definition.isEnabled(myFixture.getProject()));
        // The other instance still answers, the stopped one gets no request, and its root no other instance
        assertSameElements(wrappersOf(serversOf(file("b/two.per-folder"))), b);
        assertEmpty(serversOf(file("a/two.per-folder")));
        assertSameElements(instances(), a, b);
    }

    public void testARestartSwitchesAStoppedInstanceOnAgain() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        a.stopAndDisable();
        assertEmpty(serversOf(file("a/two.per-folder")));

        a.restart();

        waitUntilStarted(a);
        assertTrue(a.isEnabled());
        assertSameElements(wrappersOf(serversOf(file("a/two.per-folder"))), a);
    }

    public void testTooManyFailedStartsOfOneInstanceLeaveTheOthersWorking() throws Exception {
        var b = instanceOf(file("b/one.per-folder"));
        definition.failingRoot = "a";
        var file = file("a/one.per-folder");

        // Each request starts the instance again, until it gives up
        LanguageServerWrapper a = null;
        for (int i = 0; i < 30 && (a == null || a.isEnabled()); i++) {
            try {
                assertEmpty(serversOf(file));
            } catch (ExecutionException e) {
                // The request fails with the start
                assertInstanceOf(e.getCause(), CannotStartProcessException.class);
            }
            a = ContainerUtil.find(instances(), instance -> instance.getInstanceRoot() != null
                    && "a".equals(instance.getInstanceRoot().getName()));
        }

        assertNotNull(a);
        assertFalse(a.isEnabled());
        assertTrue(b.isEnabled());
        assertTrue(definition.isEnabled(myFixture.getProject()));
        assertSameElements(wrappersOf(serversOf(file("b/two.per-folder"))), b);
    }

    public void testAStopOfTheOneLanguageServerOfAProjectSwitchesTheDefinitionOff() throws Exception {
        // Without instances, as before
        definition.strategy.perFolder = false;
        var languageServer = instanceOf(file("a/one.per-folder"));
        assertNull(languageServer.getInstanceRoot());

        languageServer.stopAndDisable();

        assertFalse(definition.isEnabled(myFixture.getProject()));
        assertFalse(languageServer.isEnabled());
    }

    public void testAChangedStrategyConnectsAnOpenFileToItsNewInstance() throws Exception {
        definition.strategy.perFolder = false;
        var file = file("a/one.per-folder");
        myFixture.openFileInEditor(file.getVirtualFile());
        var instancePerProject = instanceOf(file);
        assertNull(instancePerProject.getInstanceRoot());

        definition.strategy.perFolder = true;
        fireStrategyChanged();

        waitUntilDisposed(instancePerProject);
        PlatformTestUtil.waitWithEventsDispatching("The open file must get an instance of its folder", () -> {
            var instances = instances();
            return instances.size() == 1 && instances.get(0).getInstanceRoot() != null
                    && instances.get(0).isConnectedTo(instances.get(0).toUri(file.getVirtualFile()));
        }, TIMEOUT);
        assertEquals("a", instances().get(0).getInstanceRoot().getName());
    }

    public void testAChangedStrategyRestartsOneInstancePerProject() throws Exception {
        definition.strategy.perFolder = false;
        var instance = instanceOf(file("a/one.per-folder"));

        fireStrategyChanged();

        assertFalse(instance.isDisposed());
        assertSameElements(instances(), instance);
    }

    public void testADefinitionWithoutTheOptionKeepsOneInstance() throws Exception {
        definition.strategy.perFolder = false;

        var one = instanceOf(file("a/one.per-folder"));
        var two = instanceOf(file("b/one.per-folder"));

        assertSame(one, two);
        assertNull(one.getInstanceRoot());
        assertEquals(1, instances().size());
    }

    public void testABuiltInStrategyCanAskForOneInstancePerFolder() throws Exception {
        definition.activeStrategy = new MarkersWorkspaceFolderStrategy("marker") {
            @Override
            public boolean hasServerInstances() {
                return true;
            }
        };
        file("a/marker");
        file("b/marker");

        var a = instanceOf(file("a/src/one.per-folder"));
        var otherOfA = instanceOf(file("a/two.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        assertSame(a, otherOfA);
        assertNotSame(a, b);
        assertEquals("a", a.getInstanceRoot().getName());
        assertEquals("b", b.getInstanceRoot().getName());
        assertEmpty(serversOf(file("other/one.per-folder")));
    }

    public void testAnInstanceIsInitializedWithItsFolder() throws Exception {
        var file = file("a/one.per-folder");
        var instance = instanceOf(file);

        var params = definition.servers.get(0).getInitializeParams();

        assertEquals(List.of(instance.getInstanceRoot()), params.getWorkspaceFolders());
        assertEquals(instance.toUriString(file.getVirtualFile().getParent()), params.getRootUri());
    }

    public void testAnInstanceStartsItsConnectionWithItsFolder() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));

        assertEquals(List.of(instance.getInstanceRoot()), definition.connectionFolders);
    }

    public void testEachInstanceStartsItsLanguageClientWithItsRoot() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        assertEquals(List.of(a.getInstanceRoot(), b.getInstanceRoot()), definition.languageClientRoots);
    }

    public void testAnInstanceAnswersWithItsFolder() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));

        var client = MockLanguageServerDefinition.getLastLanguageClient();
        assertNotNull(client);

        assertEquals(List.of(instance.getInstanceRoot()), client.workspaceFolders().get());
    }

    public void testAnInstanceThatGetsItsFoldersAtInitializationGetsNoChanges() throws Exception {
        instanceOf(file("a/one.per-folder"));
        instanceOf(file("a/two.per-folder"));

        assertEmpty(definition.servers.get(0).getWorkspaceService().getWorkspaceFoldersEvents());
    }

    public void testAnInstanceGetsTheWorkspaceFoldersThatBelongToIt() throws Exception {
        var file = file("a/one/file.per-folder");
        file("a/two/file.per-folder");
        file("b/one/file.per-folder");
        useTwoLevels(true, "a/one", "a/two", "b/one");

        var instance = instanceOf(file);

        var params = definition.servers.get(0).getInitializeParams();
        assertEquals(List.of("one", "two"), namesOf(params.getWorkspaceFolders()));
        assertEquals("a", instance.getInstanceRoot().getName());
        assertEquals(instance.toUriString(file.getVirtualFile().getParent().getParent()), params.getRootUri());
        var client = MockLanguageServerDefinition.getLastLanguageClient();
        assertNotNull(client);
        assertEquals(List.of("one", "two"), namesOf(client.workspaceFolders().get()));
        assertEmpty(definition.servers.get(0).getWorkspaceService().getWorkspaceFoldersEvents());
    }

    public void testFilesOfTwoFoldersOfOneInstanceShareIt() throws Exception {
        useTwoLevels(true);

        var one = instanceOf(file("a/one/file.per-folder"));
        var two = instanceOf(file("a/two/file.per-folder"));
        var other = instanceOf(file("b/one/file.per-folder"));

        assertSame(one, two);
        assertNotSame(one, other);
        assertEquals(2, definition.servers.size());
    }

    public void testAnInstanceGetsItsRootIfNoFolderBelongsToIt() throws Exception {
        file("b/one/file.per-folder");
        useTwoLevels(true, "b/one");

        var instance = instanceOf(file("a/one/file.per-folder"));

        assertEquals(List.of(instance.getInstanceRoot()), definition.servers.get(0).getInitializeParams().getWorkspaceFolders());
    }

    public void testALazyInstanceGetsItsFoldersWhenTheirFilesAreOpened() throws Exception {
        useTwoLevels(false);

        instanceOf(file("a/one/file.per-folder"));
        var serverOfA = definition.servers.get(0);
        assertEmpty(serverOfA.getInitializeParams().getWorkspaceFolders());
        waitForAddedFolders(serverOfA, "one");

        instanceOf(file("a/one/other.per-folder"));
        instanceOf(file("a/two/file.per-folder"));
        waitForAddedFolders(serverOfA, "one", "two");

        instanceOf(file("b/three/file.per-folder"));
        waitForAddedFolders(definition.servers.get(1), "three");
        assertEquals(List.of("one", "two"), addedFolders(serverOfA));
    }

    public void testALazyInstanceSendsNothingForAFileWithoutAFolder() throws Exception {
        useTwoLevels(false);

        instanceOf(file("a/file.per-folder"));
        instanceOf(file("a/one/file.per-folder"));

        waitForAddedFolders(definition.servers.get(0), "one");
    }

    public void testAFolderThatSpansInstancesGivesTheRoot() throws Exception {
        var strategy = useTwoLevels(true);
        strategy.folders.add(strategy.base);
        strategy.folderOfEachFile = strategy.base;

        var instance = instanceOf(file("a/two/file.per-folder"));

        assertEquals(List.of(instance.getInstanceRoot()), definition.servers.get(0).getInitializeParams().getWorkspaceFolders());
    }

    public void testALazyInstanceGetsItsRootForAFolderThatSpansInstances() throws Exception {
        var strategy = useTwoLevels(false);
        strategy.folderOfEachFile = strategy.base;

        instanceOf(file("a/two/file.per-folder"));

        waitForAddedFolders(definition.servers.get(0), "a");
    }

    public void testAnInstanceCanHaveFoldersThatAreNotNested() throws Exception {
        var file = file("a/one/file.per-folder");
        var otherFile = file("c/two/file.per-folder");
        var strategy = useTwoLevels(true, "a/one", "c/two");
        strategy.rootOfDirectories.put("c", "a");

        var instance = instanceOf(file);

        assertSame(instance, instanceOf(otherFile));
        assertEquals(List.of("one", "two"), namesOf(definition.servers.get(0).getInitializeParams().getWorkspaceFolders()));
    }

    public void testAStrategyThatFailsForADirectoryDoesNotStopAnInstance() throws Exception {
        var file = file("a/one/file.per-folder");
        var strategy = useTwoLevels(true, "a/one");
        strategy.failingForDirectories = true;

        var instance = instanceOf(file);

        assertEquals(List.of(instance.getInstanceRoot()), definition.servers.get(0).getInitializeParams().getWorkspaceFolders());
    }

    public void testAChangedStrategyConnectsAnOpenFileWithoutALanguageServer() throws Exception {
        var file = file("other/one.per-folder");
        myFixture.openFileInEditor(file.getVirtualFile());
        assertEmpty(serversOf(file));
        waitUntilOpeningTheFileIsDone();
        assertEmpty(instances());

        definition.strategy.perFolder = false;
        fireStrategyChanged();

        PlatformTestUtil.waitWithEventsDispatching("The open file must get a language server", () -> {
            var instances = instances();
            return instances.size() == 1 && instances.get(0).isConnectedTo(instances.get(0).toUri(file.getVirtualFile()));
        }, TIMEOUT);
    }

    public void testOneDirectoryIsOneInstanceHoweverTheStrategyWritesItsRoot() throws Exception {
        definition.strategy.otherWayFor = "two.per-folder";

        var one = instanceOf(file("a/one.per-folder"));
        var two = instanceOf(file("a/two.per-folder"));

        assertSame(one, two);
        assertEquals(List.of(one), instances());
    }

    public void testTheClientFeaturesThatGiveTheStrategyAreDisposedWithIt() {
        assertTrue(definition.hasServerInstances());
        assertEquals(0, definition.disposedWithoutLanguageServer.get());

        // As a changed configuration does
        definition.resetWorkspaceFolderStrategy();
        assertEquals(1, definition.disposedWithoutLanguageServer.get());

        assertTrue(definition.hasServerInstances());
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
        assertEquals(2, definition.disposedWithoutLanguageServer.get());
    }

    public void testTheClientFeaturesOfAStrategyThatTwoThreadsCreateAtOnceAreAllDisposed() throws Exception {
        definition.resetWorkspaceFolderStrategy();
        int disposed = definition.disposedWithoutLanguageServer.get();
        int created = definition.strategiesWithoutLanguageServer.get();
        // Each thread waits until the other one creates a strategy too, so neither finds the strategy of the other
        var bothCreating = new CyclicBarrier(2);
        definition.strategyCreation = () -> {
            try {
                bothCreating.await(TIMEOUT, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        var executor = AppExecutorUtil.getAppExecutorService();
        var first = CompletableFuture.supplyAsync(definition::hasServerInstances, executor);
        var second = CompletableFuture.supplyAsync(definition::hasServerInstances, executor);
        assertTrue(first.get(TIMEOUT, TimeUnit.SECONDS));
        assertTrue(second.get(TIMEOUT, TimeUnit.SECONDS));
        definition.strategyCreation = null;
        assertEquals(2, definition.strategiesWithoutLanguageServer.get() - created);

        // One strategy is kept, and the client features of both are disposed: the other ones at once, the kept ones
        // with their strategy
        definition.resetWorkspaceFolderStrategy();
        assertEquals(2, definition.disposedWithoutLanguageServer.get() - disposed);
    }

    public void testAStrategyCreatedBeforeAResetIsNotKept() throws Exception {
        definition.resetWorkspaceFolderStrategy();
        var creating = new CountDownLatch(1);
        var reset = new CountDownLatch(1);
        // Only the first strategy is created before the reset, and waits for it
        definition.strategyCreation = () -> {
            definition.strategyCreation = null;
            creating.countDown();
            try {
                assertTrue(reset.await(TIMEOUT, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
        var asking = CompletableFuture.supplyAsync(definition::hasServerInstances, AppExecutorUtil.getAppExecutorService());
        assertTrue(creating.await(TIMEOUT, TimeUnit.SECONDS));

        // As a changed configuration does, while the strategy of the configuration before is created
        definition.activeStrategy = WorkspaceFolderStrategy.getDefault();
        definition.resetWorkspaceFolderStrategy();
        reset.countDown();

        assertFalse("The strategy of the changed configuration must be used", asking.get(TIMEOUT, TimeUnit.SECONDS));
        assertFalse(definition.hasServerInstances());
    }

    public void testAnOpenFileWithoutAnInstanceSaysSoOnce() throws Exception {
        var hints = hintsWithoutInstance();
        var one = file("other/one.per-folder");
        var two = file("other/two.per-folder");

        // The editor connects an open file in the background, which can say it first
        myFixture.openFileInEditor(one.getVirtualFile());
        assertEmpty(serversOf(one));
        PlatformTestUtil.waitWithEventsDispatching("An open file without an instance must say so", () -> !hints.isEmpty(), TIMEOUT);
        assertTrue(hints.get(0).getContent(), hints.get(0).getContent().contains("one.per-folder"));

        myFixture.openFileInEditor(two.getVirtualFile());
        assertEmpty(serversOf(two));
        waitUntilOpeningTheFileIsDone();
        assertEquals(1, hints.size());

        // Until the configuration changes
        fireStrategyChanged();
        assertEmpty(serversOf(two));
        PlatformTestUtil.waitWithEventsDispatching("A changed configuration must say so again", () -> hints.size() > 1, TIMEOUT);
        waitUntilOpeningTheFileIsDone();
        assertEquals(2, hints.size());
    }

    public void testAFileWithoutAnInstanceWhichIsNotOpenSaysNothing() throws Exception {
        var hints = hintsWithoutInstance();

        assertEmpty(serversOf(file("other/one.per-folder")));

        assertEmpty(hints);
    }

    public void testAStopWaitsForTheStopThatIsRunning() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        var shutdown = new CompletableFuture<Void>();
        definition.servers.get(0).holdShutdown(shutdown);

        var running = instance.stop();
        var second = instance.stop();

        assertSame(running, second);
        assertFalse(second.isDone());
        shutdown.complete(null);
        waitFor(second);
    }

    public void testADocumentOfTheServerBelongsToItsInstance() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        var other = instanceOf(file("b/one.per-folder"));
        var server = definition.servers.get(0);
        var provider = new DocumentContentProvider(myFixture.getProject(), server, () -> List.of("lsp-source"));
        Disposer.register(getTestRootDisposable(), provider);
        instance.getClientFeatures().installDocumentContentProvider(provider);
        server.getWorkspaceService().setDocumentContent("lsp-source:///library.per-folder", "text");

        var document = waitFor(provider.resolve("lsp-source:///library.per-folder"));

        assertNotNull(document);
        var psiFile = ReadAction.compute(() -> PsiManager.getInstance(myFixture.getProject()).findFile(document));
        assertTrue(accessor.hasAny(psiFile, candidate -> candidate == instance));
        assertFalse(accessor.hasAny(psiFile, candidate -> candidate == other));
        assertSame(instance, instanceOf(psiFile));
    }

    public void testALazyLanguageServerWithoutInstancesGetsAllItsFolders() throws Exception {
        var strategy = useTwoLevels(false);
        strategy.instances = false;
        definition.resetWorkspaceFolderStrategy();

        instanceOf(file("a/one/file.per-folder"));
        instanceOf(file("b/two/file.per-folder"));

        assertEquals(1, definition.servers.size());
        waitForAddedFolders(definition.servers.get(0), "one", "two");
    }

    public void testAConfiguredStrategyGivesAnInstanceItsRoot() throws Exception {
        file("a/marker");
        useConfiguredStrategy("{\"serverInstances\": {\"markers\": [\"marker\"]}}");

        var instance = instanceOf(file("a/src/one.per-folder"));

        assertEquals("a", instance.getInstanceRoot().getName());
        assertEquals(List.of(instance.getInstanceRoot()), definition.servers.get(0).getInitializeParams().getWorkspaceFolders());
    }

    public void testRootTypeNoneGivesAnInstanceNoFolder() throws Exception {
        file("a/marker");
        useConfiguredStrategy("{\"serverInstances\": {\"markers\": [\"marker\"]}, \"rootType\": \"NONE\"}");

        instanceOf(file("a/src/one.per-folder"));
        instanceOf(file("a/src/two.per-folder"));

        var server = definition.servers.get(0);
        assertEmpty(server.getInitializeParams().getWorkspaceFolders());
        assertEmpty(server.getWorkspaceService().getWorkspaceFoldersEvents());
    }

    public void testAReportOfAnotherInstanceDoesNotTakeAFile() throws Exception {
        var other = instanceOf(file("b/one.per-folder"));
        var file = file("a/one.per-folder");
        // The file is open in the editor, and it isn't connected to an instance yet
        definition.strategy.noFolders = true;
        myFixture.openFileInEditor(file.getVirtualFile());
        waitUntilOpeningTheFileIsDone();
        definition.strategy.noFolders = false;

        other.updateDiagnostics(file.getVirtualFile(), "report", List.of(), null);

        assertTrue(other.isConnectedTo(other.toUri(file.getVirtualFile())));
        assertFalse(other.isSynchronizing(file.getVirtualFile()));
        var instance = instanceOf(file);
        assertEquals("a", instance.getInstanceRoot().getName());
        assertFalse(accessor.hasAny(file, server -> server == other));
    }

    public void testAChangedCommandDoesNotStartAnInstanceThatStoppedWhenIdle() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        waitFor(instance.stop());

        fireCommandChanged();

        waitUntilDisposed(instance);
        assertEmpty(instances());
        assertEquals(1, definition.servers.size());
    }

    public void testAChangedCommandGivesAnOpenFileANewInstance() throws Exception {
        var file = file("a/one.per-folder");
        myFixture.openFileInEditor(file.getVirtualFile());
        var instance = instanceOf(file);

        fireCommandChanged();

        waitUntilDisposed(instance);
        PlatformTestUtil.waitWithEventsDispatching("The open file must get a new instance", () -> {
            var instances = instances();
            return instances.size() == 1 && instances.get(0).isSynchronizing(file.getVirtualFile());
        }, TIMEOUT);
        assertEquals("a", instances().get(0).getInstanceRoot().getName());
    }

    public void testAChangedCommandRestartsOneInstancePerProject() throws Exception {
        definition.strategy.perFolder = false;
        var instance = instanceOf(file("a/one.per-folder"));

        fireCommandChanged();

        assertFalse(instance.isDisposed());
        assertSameElements(instances(), instance);
        PlatformTestUtil.waitWithEventsDispatching("The language server must start again",
                () -> definition.servers.size() == 2, TIMEOUT);
    }

    public void testChangedInitializationOptionsRestartEachInstanceOnce() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));

        fireInitializationOptionsChanged();

        waitUntilStarted(a);
        waitUntilStarted(b);
        assertEquals(4, definition.servers.size());
    }

    public void testChangedInitializationOptionsDoNotStartAnInstanceThatStoppedWhenIdle() throws Exception {
        var a = instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));
        waitFor(a.stop());

        fireInitializationOptionsChanged();

        waitUntilStarted(b);
        assertEquals(ServerStatus.stopped, a.getServerStatus());
        assertEquals(3, definition.servers.size());
    }

    public void testThePassesOverTheInstancesAskTheStrategyOnceForAFile() throws Exception {
        instanceOf(file("a/one.per-folder"));
        var b = instanceOf(file("b/one.per-folder"));
        var file = file("b/two.per-folder");
        var virtualFile = file.getVirtualFile();
        definition.strategy.askedFiles.clear();

        // They run as the file is edited, on the UI thread, so the root of the file is remembered
        assertTrue(accessor.hasAny(file, server -> server == b));
        accessor.processLanguageServers(file, server -> assertSame(b, server));
        accessor.getModificationTrackers(file);
        assertTrue(accessor.hasAny(file, server -> server == b));
        assertEquals(1, askedFor(virtualFile));

        // Until the files of the project change, which can add or remove a marker
        file("c/one.per-folder");
        assertTrue(accessor.hasAny(file, server -> server == b));
        assertEquals(2, askedFor(virtualFile));
    }

    public void testAConnectedFileNeedsNoStrategy() throws Exception {
        var file = file("a/one.per-folder");
        var instance = instanceOf(file);
        instanceOf(file("b/one.per-folder"));
        assertTrue(instance.isSynchronizing(file.getVirtualFile()));

        definition.strategy.askedFiles.clear();

        assertTrue(accessor.hasAny(file, server -> server == instance));
        accessor.processLanguageServers(file, server -> assertSame(instance, server));
        accessor.getModificationTrackers(file);
        assertEquals(0, askedFor(file.getVirtualFile()));
    }

    public void testAConnectedFileBelongsOnlyToItsInstance() throws Exception {
        myFixture.addFileToProject("a/src/one.per-folder", "text");
        var strategy = useTwoLevels(false);
        var file = file("a/src/two.per-folder");
        var a = instanceOf(file);
        var b = instanceOf(file("b/src/one.per-folder"));

        strategy.rootOfDirectories.put("a", "b");

        assertTrue(accessor.hasAny(file, server -> server == a));
        assertFalse(accessor.hasAny(file, server -> server == b));
        var trackers = accessor.getModificationTrackers(file);
        assertTrue(trackers.contains(a.getModificationTracker()));
        assertFalse(trackers.contains(b.getModificationTracker()));
    }

    private void useConfiguredStrategy(@NotNull String json) {
        var strategy = new ConfigurableWorkspaceFolderStrategy();
        strategy.configure(json);
        definition.activeStrategy = strategy;
        definition.resetWorkspaceFolderStrategy();
    }

    private @NotNull TwoLevelStrategy useTwoLevels(boolean sendAll, @NotNull String... folders) {
        var strategy = new TwoLevelStrategy(file("base.txt").getVirtualFile().getParent(), sendAll);
        for (var folder : folders) {
            strategy.folders.add(myFixture.findFileInTempDir(folder));
        }
        definition.activeStrategy = strategy;
        definition.resetWorkspaceFolderStrategy();
        return strategy;
    }

    private static @NotNull List<String> namesOf(@NotNull List<WorkspaceFolder> folders) {
        return folders.stream().map(WorkspaceFolder::getName).toList();
    }

    private static @NotNull List<String> addedFolders(@NotNull MockLanguageServer server) {
        return server.getWorkspaceService().getWorkspaceFoldersEvents().stream()
                .flatMap(params -> params.getEvent().getAdded().stream())
                .map(WorkspaceFolder::getName)
                .toList();
    }

    private static void waitForAddedFolders(@NotNull MockLanguageServer server, @NotNull String... names) {
        PlatformTestUtil.waitWithEventsDispatching("The language server must hear about the folders",
                () -> addedFolders(server).size() >= names.length, TIMEOUT);
        assertEquals(List.of(names), addedFolders(server));
    }

    /**
     * Collects the notifications that say that an open file gets no instance of the definition of the tests.
     */
    private @NotNull List<Notification> hintsWithoutInstance() {
        List<Notification> hints = new CopyOnWriteArrayList<>();
        myFixture.getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                if (LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID.equals(notification.getGroupId())
                        && definition.getDisplayName().equals(notification.getTitle())) {
                    hints.add(notification);
                }
            }
        });
        return hints;
    }

    private static List<LanguageServerWrapper> wrappersOf(List<LanguageServerItem> servers) {
        return servers.stream().map(LanguageServerItem::getServerWrapper).toList();
    }

    private static void waitUntilDisposed(@NotNull LanguageServerWrapper instance) {
        PlatformTestUtil.waitWithEventsDispatching("The replaced instance must be disposed", instance::isDisposed, TIMEOUT);
    }

    private void waitUntilStarted(@NotNull LanguageServerWrapper instance) {
        PlatformTestUtil.waitWithEventsDispatching("The instance must start again",
                () -> instance.getServerStatus() == ServerStatus.started, TIMEOUT);
    }

    /**
     * Waits until everything that opening a file in the editor asks for is done.
     */
    private void waitUntilOpeningTheFileIsDone() {
        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
        myFixture.doHighlighting();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    }

    private long askedFor(@NotNull VirtualFile file) {
        return definition.strategy.askedFiles.stream().filter(file::equals).count();
    }

    private void fireCommandChanged() {
        LanguageServersRegistry.getInstance().handleChangeEvent(new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                LanguageServerDefinitionListener.LanguageServerDefinitionEvent.UpdatedBy.USER, myFixture.getProject(), definition,
                false, true, false, false, false, false, false, false, false));
    }

    private void fireInitializationOptionsChanged() {
        ProjectLanguageServerSettings.getInstance(myFixture.getProject()).handleChanged(
                new LanguageServerSettingsListener.LanguageServerSettingsChangedEvent(definition.getId(),
                        new LanguageServerSettings.LanguageServerDefinitionSettings(),
                        false, false, false, true, false, false, false, false, false, false));
    }

    private void fireStrategyChanged() {
        LanguageServersRegistry.getInstance().handleChangeEvent(new LanguageServerDefinitionListener.LanguageServerChangedEvent(
                LanguageServerDefinitionListener.LanguageServerDefinitionEvent.UpdatedBy.USER, myFixture.getProject(), definition,
                false, false, false, false, false, false, false, false, true));
    }

    private @NotNull PsiFile file(@NotNull String path) {
        return myFixture.addFileToProject(path, "text");
    }

    private void move(@NotNull VirtualFile file, @NotNull VirtualFile target) throws IOException {
        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> file.move(this, target));
    }

    private @NotNull LanguageServerWrapper instanceOf(@NotNull PsiFile file) throws Exception {
        var servers = serversOf(file);
        assertEquals(1, servers.size());
        return servers.get(0).getServerWrapper();
    }

    private @NotNull List<LanguageServerItem> serversOf(@NotNull PsiFile file) throws Exception {
        return waitFor(accessor.getLanguageServers(file, null, null));
    }

    private @NotNull List<LanguageServerItem> allServers() throws Exception {
        return waitFor(accessor.getLanguageServers(features -> features.getServerDefinition() == definition, null));
    }

    private static <T> T waitFor(@NotNull CompletableFuture<T> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", future::isDone, TIMEOUT);
        return future.get();
    }

    private @NotNull List<LanguageServerWrapper> instances() {
        return accessor.getStartedServers().stream().filter(server -> server.getServerDefinition() == definition).toList();
    }

    private static final class FolderStrategy implements WorkspaceFolderStrategy {

        volatile boolean perFolder = true;
        volatile boolean noFolders = false;
        volatile boolean failing = false;
        // The file whose folder is written with a trailing slash if it has none, and without one if it has
        volatile @Nullable String otherWayFor;
        final List<VirtualFile> askedFiles = new CopyOnWriteArrayList<>();
        // Is given each file that the strategy is asked for, once its folder is chosen
        volatile @Nullable Consumer<VirtualFile> afterAsked;

        @Override
        public boolean hasServerInstances() {
            return perFolder;
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
            askedFiles.add(file);
            if (failing) {
                throw new IllegalStateException("No workspace folder");
            }
            var folder = folderOf(file, fileUriSupport);
            var asked = afterAsked;
            if (asked != null) {
                asked.accept(file);
            }
            return folder;
        }

        private @Nullable WorkspaceFolder folderOf(@NotNull VirtualFile file, @NotNull FileUriSupport fileUriSupport) {
            var parent = file.getParent();
            if (noFolders || parent == null || !List.of("a", "b").contains(parent.getName())) {
                return null;
            }
            var uri = FileUriSupport.toString(parent, fileUriSupport);
            if (file.getName().equals(otherWayFor)) {
                uri = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri + "/";
            }
            return new WorkspaceFolder(uri, parent.getName());
        }
    }

    /**
     * The first directory of a path is the root of an instance, and the directory in it a workspace folder.
     */
    private static final class TwoLevelStrategy implements WorkspaceFolderStrategy {

        final VirtualFile base;
        final boolean sendAll;
        final List<VirtualFile> folders = new CopyOnWriteArrayList<>();
        final Map<String, String> rootOfDirectories = new ConcurrentHashMap<>();
        volatile @Nullable VirtualFile folderOfEachFile;
        volatile boolean failingForDirectories = false;
        volatile boolean instances = true;

        TwoLevelStrategy(@NotNull VirtualFile base, boolean sendAll) {
            this.base = base;
            this.sendAll = sendAll;
        }

        @Override
        public boolean hasServerInstances() {
            return instances;
        }

        @Override
        public boolean sendAllFoldersOnInitialization() {
            return sendAll;
        }

        @Override
        public @NotNull List<WorkspaceFolder> getWorkspaceFolders(@NotNull Project project,
                                                                  @NotNull FileUriSupport fileUriSupport) {
            return folders.stream().map(folder -> toFolder(folder, fileUriSupport)).toList();
        }

        @Override
        public @Nullable WorkspaceFolder getServerInstanceRoot(@NotNull VirtualFile file,
                                                               @NotNull Project project,
                                                               @NotNull FileUriSupport fileUriSupport) {
            if (failingForDirectories && file.isDirectory()) {
                throw new IllegalStateException("No directories");
            }
            var directory = directoryAt(file, 0);
            if (directory == null) {
                return null;
            }
            var root = rootOfDirectories.get(directory.getName());
            return toFolder(root != null ? directory.getParent().findChild(root) : directory, fileUriSupport);
        }

        @Override
        public @Nullable WorkspaceFolder getWorkspaceFolderForFile(@NotNull VirtualFile file,
                                                                   @NotNull Project project,
                                                                   @NotNull FileUriSupport fileUriSupport) {
            var folder = folderOfEachFile != null ? folderOfEachFile : directoryAt(file, 1);
            return folder != null ? toFolder(folder, fileUriSupport) : null;
        }

        private static @NotNull WorkspaceFolder toFolder(@NotNull VirtualFile directory, @NotNull FileUriSupport fileUriSupport) {
            return new WorkspaceFolder(FileUriSupport.toString(directory, fileUriSupport), directory.getName());
        }

        /**
         * Returns the directory on the given level below the base directory.
         */
        private @Nullable VirtualFile directoryAt(@NotNull VirtualFile file, int level) {
            var path = new ArrayList<VirtualFile>();
            for (var current = file; current != null && !base.equals(current); current = current.getParent()) {
                path.add(0, current);
            }
            return level < path.size() && path.get(level).isDirectory() ? path.get(level) : null;
        }
    }

    private static final class PerFolderDefinition extends MockLanguageServerDefinition {

        final FolderStrategy strategy = new FolderStrategy();
        volatile WorkspaceFolderStrategy activeStrategy = strategy;
        final List<MockLanguageServer> servers = new CopyOnWriteArrayList<>();
        final List<WorkspaceFolder> connectionFolders = new CopyOnWriteArrayList<>();
        final List<WorkspaceFolder> languageClientRoots = new CopyOnWriteArrayList<>();
        // The client features that gave the strategy, which have no language server
        final AtomicInteger disposedWithoutLanguageServer = new AtomicInteger();
        final AtomicInteger strategiesWithoutLanguageServer = new AtomicInteger();
        // Runs as the strategy of client features without a language server is created, after it is chosen
        volatile @Nullable Runnable strategyCreation;
        // The name of the root whose instance can't start
        volatile @Nullable String failingRoot;
        // The name of the root whose instance has a language client and client features that fail as they are
        // disposed, the language client as a stop disposes it, and the client features as the dispose of the instance
        // does, after the stop
        volatile @Nullable String failingClientRoot;
        // What that language client throws as it is disposed, one at each dispose, until they are used up. A stop
        // that fails there leaves the connection open, which the dispose then closes
        final Queue<RuntimeException> clientFailures = new ConcurrentLinkedQueue<>();
        // What these client features throw as they are disposed, one at each dispose, until they are used up
        final Queue<RuntimeException> clientFeaturesFailures = new ConcurrentLinkedQueue<>();
        // Holds the initialize replies of the instances that start, until it completes
        volatile @Nullable CompletableFuture<Void> initializeGate;
        // Is given each file whose URI the client features are asked for
        volatile @Nullable Consumer<VirtualFile> uriAsked;

        PerFolderDefinition() {
            super("per-folder-server", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var server = new MockLanguageServer(PerFolderDefinition::capabilities);
            var gate = initializeGate;
            if (gate != null) {
                var held = server.holdInitialize();
                gate.thenRun(() -> held.complete(null));
            }
            servers.add(server);
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                          @NotNull WorkspaceFolder instanceRoot) {
            connectionFolders.add(instanceRoot);
            if (instanceRoot.getName().equals(failingRoot)) {
                return new MockConnectionProvider(new MockLanguageServer(PerFolderDefinition::capabilities)) {
                    @Override
                    public void start() throws CannotStartProcessException {
                        throw new CannotStartProcessException("Cannot run program \"missing\"");
                    }
                };
            }
            return super.createConnectionProvider(project, instanceRoot);
        }

        @Override
        public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project,
                                                                @NotNull WorkspaceFolder instanceRoot) {
            languageClientRoots.add(instanceRoot);
            if (instanceRoot.getName().equals(failingClientRoot)) {
                return new LanguageClientImpl(project) {
                    @Override
                    public void dispose() {
                        super.dispose();
                        var failure = clientFailures.poll();
                        if (failure != null) {
                            throw failure;
                        }
                    }
                };
            }
            return super.createLanguageClient(project, instanceRoot);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            var features = new LSPClientFeatures() {
                @Override
                public void dispose() {
                    var languageServer = getServerWrapper();
                    super.dispose();
                    if (languageServer == null) {
                        disposedWithoutLanguageServer.incrementAndGet();
                    } else if (languageServer.getInstanceRoot() != null
                            && languageServer.getInstanceRoot().getName().equals(failingClientRoot)) {
                        var failure = clientFeaturesFailures.poll();
                        if (failure != null) {
                            throw failure;
                        }
                    }
                }

                @Override
                public @Nullable URI getFileUri(@NotNull VirtualFile file) {
                    var asked = uriAsked;
                    if (asked != null) {
                        asked.accept(file);
                    }
                    return super.getFileUri(file);
                }
            };
            return features.setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override
                protected @NotNull WorkspaceFolderStrategy createStrategy() {
                    var created = activeStrategy;
                    if (getClientFeatures().getServerWrapper() == null) {
                        strategiesWithoutLanguageServer.incrementAndGet();
                        var creation = strategyCreation;
                        if (creation != null) {
                            creation.run();
                        }
                    }
                    return created;
                }
            });
        }

        private static ServerCapabilities capabilities() {
            var folders = new WorkspaceFoldersOptions();
            folders.setSupported(true);
            folders.setChangeNotifications(true);
            var workspace = new WorkspaceServerCapabilities();
            workspace.setWorkspaceFolders(folders);
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setWorkspace(workspace);
            return capabilities;
        }
    }
}

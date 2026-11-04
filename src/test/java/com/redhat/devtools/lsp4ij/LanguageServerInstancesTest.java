/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPWorkspaceFolderFeature;
import com.redhat.devtools.lsp4ij.features.documentContent.DocumentContentProvider;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.ConfigurableWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.MarkersWorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests a language server definition whose workspace folder strategy spreads the files over several instances. By
 * default, each workspace folder has an instance. The folders of these tests are "a" and "b", and a file in any other
 * folder has none. {@link TwoLevelStrategy} separates the instances from the folders.
 */
public class LanguageServerInstancesTest extends LSPCodeInsightFixtureTestCase {

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

    public void testAReplacedInstanceIsDisposedOnTheUiThread() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        var disposedOnTheUiThread = new AtomicBoolean();
        Disposer.register(instance, () -> disposedOnTheUiThread.set(ApplicationManager.getApplication().isDispatchThread()));

        fireStrategyChanged();
        waitUntilDisposed(instance);

        assertTrue(disposedOnTheUiThread.get());
    }

    public void testAChangedStrategyEnablesTheDefinitionAgain() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        instance.stopAndDisable();
        assertFalse(definition.isEnabled(myFixture.getProject()));

        fireStrategyChanged();

        assertTrue(definition.isEnabled(myFixture.getProject()));
        assertNotSame(instance, instanceOf(file("a/two.per-folder")));
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
        }, 10);
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
        }, 10);
    }

    public void testAStopWaitsForTheStopThatIsRunning() throws Exception {
        var instance = instanceOf(file("a/one.per-folder"));
        definition.servers.get(0).setTimeToProceedQueries(1000);

        var running = instance.stop();
        var second = instance.stop();

        assertSame(running, second);
        assertFalse(second.isDone());
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
        }, 10);
        assertEquals("a", instances().get(0).getInstanceRoot().getName());
    }

    public void testAChangedCommandRestartsOneInstancePerProject() throws Exception {
        definition.strategy.perFolder = false;
        var instance = instanceOf(file("a/one.per-folder"));

        fireCommandChanged();

        assertFalse(instance.isDisposed());
        assertSameElements(instances(), instance);
        PlatformTestUtil.waitWithEventsDispatching("The language server must start again",
                () -> definition.servers.size() == 2, 10);
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

    public void testAPassOverTheInstancesAsksTheStrategyOnce() throws Exception {
        instanceOf(file("a/one.per-folder"));
        instanceOf(file("b/one.per-folder"));
        var file = file("b/two.per-folder");
        var virtualFile = file.getVirtualFile();

        definition.strategy.askedFiles.clear();
        assertTrue(accessor.hasAny(file, server -> true));
        assertEquals(1, askedFor(virtualFile));

        definition.strategy.askedFiles.clear();
        accessor.processLanguageServers(file, server -> {
        });
        assertEquals(1, askedFor(virtualFile));

        definition.strategy.askedFiles.clear();
        accessor.getModificationTrackers(file);
        assertEquals(1, askedFor(virtualFile));
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
                () -> addedFolders(server).size() >= names.length, 10);
        assertEquals(List.of(names), addedFolders(server));
    }

    private static List<LanguageServerWrapper> wrappersOf(List<LanguageServerItem> servers) {
        return servers.stream().map(LanguageServerItem::getServerWrapper).toList();
    }

    private static void waitUntilDisposed(@NotNull LanguageServerWrapper instance) {
        PlatformTestUtil.waitWithEventsDispatching("The replaced instance must be disposed", instance::isDisposed, 10);
    }

    private void waitUntilStarted(@NotNull LanguageServerWrapper instance) {
        PlatformTestUtil.waitWithEventsDispatching("The instance must start again",
                () -> instance.getServerStatus() == ServerStatus.started, 10);
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
        PlatformTestUtil.waitWithEventsDispatching("The language servers must answer", future::isDone, 10);
        return future.get();
    }

    private @NotNull List<LanguageServerWrapper> instances() {
        return accessor.getStartedServers().stream().filter(server -> server.getServerDefinition() == definition).toList();
    }

    private static final class FolderStrategy implements WorkspaceFolderStrategy {

        volatile boolean perFolder = true;
        volatile boolean noFolders = false;
        volatile boolean failing = false;
        final List<VirtualFile> askedFiles = new CopyOnWriteArrayList<>();

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
            var parent = file.getParent();
            if (noFolders || parent == null || !List.of("a", "b").contains(parent.getName())) {
                return null;
            }
            return new WorkspaceFolder(FileUriSupport.toString(parent, fileUriSupport), parent.getName());
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

        PerFolderDefinition() {
            super("per-folder-server", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            var server = new MockLanguageServer(PerFolderDefinition::capabilities);
            servers.add(server);
            return new MockConnectionProvider(server);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                          @NotNull WorkspaceFolder instanceRoot) {
            connectionFolders.add(instanceRoot);
            return super.createConnectionProvider(project, instanceRoot);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return super.createClientFeatures().setWorkspaceFolderFeature(new LSPWorkspaceFolderFeature() {
                @Override
                protected @NotNull WorkspaceFolderStrategy createStrategy() {
                    return activeStrategy;
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

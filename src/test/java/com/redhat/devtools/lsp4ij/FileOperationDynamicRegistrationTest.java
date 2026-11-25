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
package com.redhat.devtools.lsp4ij;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPCallHierarchyFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockWorkspaceService;
import com.redhat.devtools.lsp4ij.server.capabilities.CallHierarchyCapabilityRegistry;
import org.eclipse.lsp4j.CallHierarchyRegistrationOptions;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A language server which announces no file operations in its capabilities, and registers them dynamically, is told of
 * the creations, renames and deletions of the files that the filters of its registrations match, until it unregisters
 * them. A registration which the server withdraws while it initializes, before LSP4IJ installs it, is never installed,
 * and one which it withdraws while LSP4IJ installs it is removed once installed, without removing a registration of the
 * same id that the server made since: neither its file system watchers, nor its commands, nor the workspace folders it
 * turned on.
 */
public class FileOperationDynamicRegistrationTest extends LSPCodeInsightFixtureTestCase {

    private static final FileOperationOptions FOO_FILES =
            new FileOperationOptions(List.of(new FileOperationFilter(new FileOperationPattern("**/*.foo"))));

    // The write actions running now
    private final AtomicInteger writeActions = new AtomicInteger();
    // Tells apart the notifications that the test sends to know that the notifications before them have arrived
    private final AtomicInteger markers = new AtomicInteger();

    public FileOperationDynamicRegistrationTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The default capabilities of the mock language server have no file operations
        MockLanguageServer.reset();
        ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
            @Override
            public void writeActionStarted(@NotNull Object action) {
                writeActions.incrementAndGet();
            }

            @Override
            public void writeActionFinished(@NotNull Object action) {
                writeActions.decrementAndGet();
            }
        }, getTestRootDisposable());
    }

    public void testRenamesThatADynamicRegistrationMatchesAreSentUntilItIsUnregistered() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "");
        var notes = myFixture.getTempDirFixture().createFile("notes.txt", "");
        var workspace = workspace();

        register(new Registration("will-rename", "workspace/willRenameFiles", FOO_FILES),
                new Registration("did-rename", "workspace/didRenameFiles", FOO_FILES));

        rename(renamed, "moved.foo");
        PlatformTestUtil.waitWithEventsDispatching("a rename that the filters match must send workspace/willRenameFiles",
                () -> !workspace.getWillRenameRequests().isEmpty(), 10);
        assertRename(workspace.getWillRenameRequests().get(0), "/renamed.foo", "/moved.foo");
        PlatformTestUtil.waitWithEventsDispatching("a rename that the filters match must send workspace/didRenameFiles",
                () -> !workspace.getDidRenameNotifications().isEmpty(), 10);
        assertRename(workspace.getDidRenameNotifications().get(0), "/renamed.foo", "/moved.foo");

        rename(notes, "todo.txt");
        awaitTheNotificationsSentSoFar(wrapper);
        assertEquals("a rename that the filters don't match must not send workspace/willRenameFiles",
                1, workspace.getWillRenameRequests().size());
        assertEquals("a rename that the filters don't match must not send workspace/didRenameFiles",
                1, workspace.getDidRenameNotifications().size());

        unregister(new Unregistration("will-rename", "workspace/willRenameFiles"),
                new Unregistration("did-rename", "workspace/didRenameFiles"));

        rename(renamed, "final.foo");
        awaitTheNotificationsSentSoFar(wrapper);
        assertEquals("after the unregistration a rename must not send workspace/willRenameFiles",
                1, workspace.getWillRenameRequests().size());
        assertEquals("after the unregistration a rename must not send workspace/didRenameFiles",
                1, workspace.getDidRenameNotifications().size());
    }

    public void testCreationsAndDeletionsThatADynamicRegistrationMatchesAreSentUntilItIsUnregistered() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var workspace = workspace();

        // LSP4IJ doesn't announce workspace/willCreateFiles, and ignores its registration
        register(new Registration("will-create", "workspace/willCreateFiles", FOO_FILES),
                new Registration("did-create", "workspace/didCreateFiles", FOO_FILES),
                new Registration("did-delete", "workspace/didDeleteFiles", FOO_FILES));

        var created = myFixture.getTempDirFixture().createFile("created.foo", "");
        PlatformTestUtil.waitWithEventsDispatching("a creation that the filters match must send workspace/didCreateFiles",
                () -> !workspace.getDidCreateNotifications().isEmpty(), 10);
        assertTrue(workspace.getDidCreateNotifications().get(0).getFiles().get(0).getUri().endsWith("/created.foo"));
        delete(created);
        PlatformTestUtil.waitWithEventsDispatching("a deletion that the filters match must send workspace/didDeleteFiles",
                () -> !workspace.getDidDeleteNotifications().isEmpty(), 10);
        assertTrue(workspace.getDidDeleteNotifications().get(0).getFiles().get(0).getUri().endsWith("/created.foo"));

        delete(myFixture.getTempDirFixture().createFile("created.txt", ""));
        awaitTheNotificationsSentSoFar(wrapper);
        assertEquals("a creation that the filters don't match must not send workspace/didCreateFiles",
                1, workspace.getDidCreateNotifications().size());
        assertEquals("a deletion that the filters don't match must not send workspace/didDeleteFiles",
                1, workspace.getDidDeleteNotifications().size());
        assertEquals("a registration of workspace/willCreateFiles must be ignored", 0, workspace.getWillCreateRequests().size());

        unregister(new Unregistration("did-create", "workspace/didCreateFiles"),
                new Unregistration("did-delete", "workspace/didDeleteFiles"));

        delete(myFixture.getTempDirFixture().createFile("again.foo", ""));
        awaitTheNotificationsSentSoFar(wrapper);
        assertEquals("after the unregistration a creation must not send workspace/didCreateFiles",
                1, workspace.getDidCreateNotifications().size());
        assertEquals("after the unregistration a deletion must not send workspace/didDeleteFiles",
                1, workspace.getDidDeleteNotifications().size());
    }

    public void testARenameRefactoringAsksADynamicRegistrationBeforeItsWriteAction() throws Exception {
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "");
        connect(renamed);
        register(new Registration("will-rename", "workspace/willRenameFiles", FOO_FILES));
        var willRename = new CompletableFuture<RenameFilesParams>();
        // Runs when the server receives the request
        var askedInWriteAction = willRename.thenApply(params -> writeActions.get() > 0);
        workspace().setWillRenameCallback(willRename);

        new RenameProcessor(myFixture.getProject(), PsiManager.getInstance(myFixture.getProject()).findFile(renamed),
                "moved.foo", false, false).run();

        PlatformTestUtil.waitWithEventsDispatching("the rename refactoring must send workspace/willRenameFiles",
                askedInWriteAction::isDone, 10);
        assertFalse("workspace/willRenameFiles must be sent before the write action of the refactoring",
                askedInWriteAction.join());
        assertEquals("the rename must send workspace/willRenameFiles once", 1, workspace().getWillRenameRequests().size());
        assertEquals("moved.foo", renamed.getName());
    }

    public void testARegistrationWithdrawnWhileTheServerInitializesIsNotInstalled() throws Exception {
        var psiManager = PsiManager.getInstance(myFixture.getProject());
        var main = myFixture.getTempDirFixture().createFile("main.foo", "");
        var other = myFixture.getTempDirFixture().createFile("other.foo", "");
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "");
        var initialize = MockLanguageServer.INSTANCE.holdInitialize();
        var connecting = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(psiManager.findFile(main), null, null);
        try {
            PlatformTestUtil.waitWithEventsDispatching("the server must be initializing",
                    () -> MockLanguageServer.INSTANCE.getNotifications().contains("initialize"), 30);
            // LSP4IJ answers while the server initializes, and installs the registrations once it is initialized
            register(new Registration("will-rename", "workspace/willRenameFiles", FOO_FILES),
                    new Registration("did-rename", "workspace/didRenameFiles", FOO_FILES),
                    new Registration("main-call-hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/main.foo")),
                    // Installed after the others: once it is, LSP4IJ has dealt with them
                    new Registration("other-call-hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/other.foo")));
            unregister(new Unregistration("will-rename", "workspace/willRenameFiles"),
                    new Unregistration("did-rename", "workspace/didRenameFiles"),
                    new Unregistration("main-call-hierarchy", "textDocument/prepareCallHierarchy"));
        } finally {
            initialize.complete(null);
        }
        var wrapper = connecting.get(30, TimeUnit.SECONDS).get(0).getServerWrapper();
        var callHierarchy = wrapper.getClientFeatures().getCallHierarchyFeature();
        PlatformTestUtil.waitWithEventsDispatching("the registration that the server kept must be installed once it is initialized",
                () -> callHierarchy.isCallHierarchySupported(psiManager.findFile(other)), 10);

        rename(renamed, "moved.foo");
        awaitTheNotificationsSentSoFar(wrapper);
        assertEquals("a rename must not send workspace/willRenameFiles for a registration withdrawn while the server initialized",
                0, workspace().getWillRenameRequests().size());
        assertEquals("a rename must not send workspace/didRenameFiles for a registration withdrawn while the server initialized",
                0, workspace().getDidRenameNotifications().size());
        assertFalse("call hierarchy must not be supported for a registration withdrawn while the server initialized",
                callHierarchy.isCallHierarchySupported(psiManager.findFile(main)));
    }

    public void testACommandRegistrationWithdrawnWhileItIsInstalledIsRemovedOnceInstalled() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var commands = holdCommands(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(new Registration("command",
                    "workspace/executeCommand", new ExecuteCommandOptions(List.of("dynamic.command"))))));
            get(commands.adding, "LSP4IJ must install the registration by adding its command");
            // While LSP4IJ adds the command
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("command",
                    "workspace/executeCommand")))), "the client must answer the unregistration");
            commands.release.complete(null);
            get(registered, "the client must answer the registration");
        } finally {
            commands.release.complete(null);
        }
        assertFalse("a command registration withdrawn while LSP4IJ installed it must be removed once installed",
                wrapper.getServerCapabilitiesSync().getExecuteCommandProvider().getCommands().contains("dynamic.command"));
    }

    public void testTheEndOfAnInstallationLeavesARegistrationAgainOfAWithdrawnIdInPlace() throws Exception {
        var psiManager = PsiManager.getInstance(myFixture.getProject());
        var main = myFixture.getTempDirFixture().createFile("main.foo", "");
        var other = myFixture.getTempDirFixture().createFile("other.foo", "");
        var wrapper = connect(main);
        var commands = holdCommands(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(
                    new Registration("hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/main.foo")),
                    new Registration("command", "workspace/executeCommand",
                            new ExecuteCommandOptions(List.of("dynamic.command"))))));
            // Once LSP4IJ has installed "hierarchy", while it adds the command of the next registration
            get(commands.adding, "LSP4IJ must install the registrations up to the command");
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("hierarchy",
                    "textDocument/prepareCallHierarchy")))), "the client must answer the unregistration");
            get(client.registerCapability(new RegistrationParams(List.of(new Registration("hierarchy",
                            "textDocument/prepareCallHierarchy", callHierarchyOf("**/other.foo"))))),
                    "the client must answer the registration again");
            commands.release.complete(null);
            get(registered, "the client must answer the first registration");
        } finally {
            commands.release.complete(null);
        }
        var callHierarchy = wrapper.getClientFeatures().getCallHierarchyFeature();
        assertTrue("a registration again of a withdrawn id must stay installed once the first installation ends",
                callHierarchy.isCallHierarchySupported(psiManager.findFile(other)));
        assertFalse("the withdrawn registration must stay removed",
                callHierarchy.isCallHierarchySupported(psiManager.findFile(main)));
    }

    public void testAWithdrawnInstallationLeavesARegistrationAgainOfItsIdInPlace() throws Exception {
        var psiManager = PsiManager.getInstance(myFixture.getProject());
        var other = myFixture.getTempDirFixture().createFile("other.foo", "");
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var commands = holdCommands(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(new Registration("id",
                    "workspace/executeCommand", new ExecuteCommandOptions(List.of("dynamic.command"))))));
            // While LSP4IJ adds the command
            get(commands.adding, "LSP4IJ must install the registration by adding its command");
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("id",
                    "workspace/executeCommand")))), "the client must answer the unregistration");
            get(client.registerCapability(new RegistrationParams(List.of(new Registration("id",
                            "textDocument/prepareCallHierarchy", callHierarchyOf("**/other.foo"))))),
                    "the client must answer the registration again");
            commands.release.complete(null);
            get(registered, "the client must answer the first registration");
        } finally {
            commands.release.complete(null);
        }
        var callHierarchy = wrapper.getClientFeatures().getCallHierarchyFeature();
        assertFalse("a command registration withdrawn while LSP4IJ installed it must be removed once installed",
                wrapper.getServerCapabilitiesSync().getExecuteCommandProvider().getCommands().contains("dynamic.command"));
        assertTrue("a registration again of a withdrawn id must stay installed once the first installation ends",
                callHierarchy.isCallHierarchySupported(psiManager.findFile(other)));

        get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("id",
                "textDocument/prepareCallHierarchy")))), "the client must answer the second unregistration");
        assertFalse("the unregistration of the registration again must remove it",
                callHierarchy.isCallHierarchySupported(psiManager.findFile(other)));
    }

    public void testAWithdrawnInstallationLeavesTheWatchersOfARegistrationAgainOfItsIdInPlace() throws Exception {
        var main = myFixture.getTempDirFixture().createFile("main.foo", "");
        var wrapper = connect(main);
        var directory = main.getParent().toNioPath();
        var callHierarchy = holdCallHierarchy(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(
                    new Registration("watchers", "workspace/didChangeWatchedFiles", textFilesIn(directory)),
                    new Registration("hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/main.foo")))));
            // Once LSP4IJ has added the watchers, while it installs the next registration
            get(callHierarchy.adding, "LSP4IJ must install the registrations up to the call hierarchy");
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("watchers",
                    "workspace/didChangeWatchedFiles")))), "the client must answer the unregistration");
            // Equal watchers, which the end of the first installation must not take for its own
            get(client.registerCapability(new RegistrationParams(List.of(new Registration("watchers",
                            "workspace/didChangeWatchedFiles", textFilesIn(directory))))),
                    "the client must answer the registration again");
            callHierarchy.release.complete(null);
            get(registered, "the client must answer the first registration");
        } finally {
            callHierarchy.release.complete(null);
        }

        myFixture.getTempDirFixture().createFile("created.txt", "");
        PlatformTestUtil.waitWithEventsDispatching("the watchers of a registration again of a withdrawn id must stay "
                + "registered once the first installation ends, and report a change", () -> watched("/created.txt"), 10);

        unregister(new Unregistration("watchers", "workspace/didChangeWatchedFiles"));
        myFixture.getTempDirFixture().createFile("after.txt", "");
        awaitTheNotificationsSentSoFar(wrapper);
        assertFalse("the unregistration of the registration again must remove its watchers", watched("/after.txt"));
    }

    public void testAWithdrawnInstallationLeavesTheWorkspaceFoldersOfARegistrationAgainOn() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var callHierarchy = holdCallHierarchy(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(
                    new Registration("folders", "workspace/didChangeWorkspaceFolders"),
                    new Registration("hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/main.foo")))));
            // Once LSP4IJ has turned the workspace folders on, while it installs the next registration
            get(callHierarchy.adding, "LSP4IJ must install the registrations up to the call hierarchy");
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("folders",
                    "workspace/didChangeWorkspaceFolders")))), "the client must answer the unregistration");
            get(client.registerCapability(new RegistrationParams(List.of(new Registration("folders",
                            "workspace/didChangeWorkspaceFolders")))),
                    "the client must answer the registration again");
            callHierarchy.release.complete(null);
            get(registered, "the client must answer the first registration");
        } finally {
            callHierarchy.release.complete(null);
        }
        assertTrue("the workspace folders of a registration again must stay on once the first installation ends",
                supportsWorkspaceFolders(wrapper));

        unregister(new Unregistration("folders", "workspace/didChangeWorkspaceFolders"));
        assertFalse("the unregistration of the registration again must turn the workspace folders off",
                supportsWorkspaceFolders(wrapper));
    }

    public void testAWithdrawnInstallationLeavesTheCommandOfARegistrationAgainOfItsIdInPlace() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("main.foo", ""));
        var callHierarchy = holdCallHierarchy(wrapper);
        var client = MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
        try {
            var registered = client.registerCapability(new RegistrationParams(List.of(
                    new Registration("command", "workspace/executeCommand",
                            new ExecuteCommandOptions(List.of("dynamic.command"))),
                    new Registration("hierarchy", "textDocument/prepareCallHierarchy", callHierarchyOf("**/main.foo")))));
            // Once LSP4IJ has added the command, while it installs the next registration
            get(callHierarchy.adding, "LSP4IJ must install the registrations up to the call hierarchy");
            get(client.unregisterCapability(new UnregistrationParams(List.of(new Unregistration("command",
                    "workspace/executeCommand")))), "the client must answer the unregistration");
            get(client.registerCapability(new RegistrationParams(List.of(new Registration("command",
                            "workspace/executeCommand", new ExecuteCommandOptions(List.of("dynamic.command")))))),
                    "the client must answer the registration again");
            callHierarchy.release.complete(null);
            get(registered, "the client must answer the first registration");
        } finally {
            callHierarchy.release.complete(null);
        }
        assertTrue("the command of a registration again of a withdrawn id must stay once the first installation ends",
                wrapper.getServerCapabilitiesSync().getExecuteCommandProvider().getCommands().contains("dynamic.command"));

        unregister(new Unregistration("command", "workspace/executeCommand"));
        assertFalse("the unregistration of the registration again must remove its command",
                wrapper.getServerCapabilitiesSync().getExecuteCommandProvider().getCommands().contains("dynamic.command"));
    }

    /**
     * Holds the additions to the commands of the server capabilities until the test releases them.
     */
    private static HeldCommands holdCommands(LanguageServerWrapper wrapper) {
        var commandProvider = wrapper.getServerCapabilitiesSync().getExecuteCommandProvider();
        var commands = new HeldCommands(commandProvider.getCommands());
        commandProvider.setCommands(commands);
        return commands;
    }

    /**
     * The commands of the server capabilities, which LSP4IJ adds the commands of a 'workspace/executeCommand'
     * registration to: an addition waits until the test releases it.
     */
    private static final class HeldCommands extends CopyOnWriteArrayList<String> {

        private final CompletableFuture<Void> adding = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        private HeldCommands(Collection<String> commands) {
            super(commands);
        }

        @Override
        public boolean add(String command) {
            adding.complete(null);
            try {
                release.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("the test must release the addition of '" + command + "'", e);
            }
            return super.add(command);
        }
    }

    /**
     * Holds the installation of the call hierarchy registrations until the test releases it. Unlike an addition to the
     * commands, it waits without locking the language server wrapper, whose lock the installation of a registration
     * meanwhile takes, as of watchers, commands or workspace folders.
     */
    private static HeldCallHierarchy holdCallHierarchy(LanguageServerWrapper wrapper) {
        var clientFeatures = wrapper.getClientFeatures();
        var registry = new HeldCallHierarchy(clientFeatures);
        clientFeatures.setCallHierarchyFeature(new LSPCallHierarchyFeature() {
            @Override
            public CallHierarchyCapabilityRegistry getCallHierarchyCapabilityRegistry() {
                return registry;
            }
        });
        return registry;
    }

    /**
     * The call hierarchy registrations, whose installation waits until the test releases it.
     */
    private static final class HeldCallHierarchy extends CallHierarchyCapabilityRegistry {

        private final CompletableFuture<Void> adding = new CompletableFuture<>();
        private final CompletableFuture<Void> release = new CompletableFuture<>();

        private HeldCallHierarchy(LSPClientFeatures clientFeatures) {
            super(clientFeatures);
        }

        @Override
        public @Nullable CallHierarchyRegistrationOptions registerCapability(@NotNull JsonObject registerOptions) {
            adding.complete(null);
            try {
                release.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("the test must release the installation of the call hierarchy", e);
            }
            return super.registerCapability(registerOptions);
        }
    }

    /**
     * Waits for the future without dispatching the events of the EDT, which could wait for the language server wrapper
     * while a held installation locks it.
     */
    private static <T> T get(CompletableFuture<T> future, String failure) throws Exception {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError(failure, e);
        }
    }

    private static CallHierarchyRegistrationOptions callHierarchyOf(String pattern) {
        var options = new CallHierarchyRegistrationOptions();
        options.setDocumentSelector(List.of(new DocumentFilter(null, null, Either.forLeft(pattern))));
        return options;
    }

    private static DidChangeWatchedFilesRegistrationOptions textFilesIn(Path directory) {
        return new DidChangeWatchedFilesRegistrationOptions(List.of(new FileSystemWatcher(
                Either.forRight(new RelativePattern(Either.forRight(directory.toUri().toString()), "**/*.txt")))));
    }

    /**
     * Returns whether the server was told of a change to the file whose path ends with the given one.
     */
    private static boolean watched(String path) {
        return workspace().getWatchedFileEvents().stream().anyMatch(event -> event.getUri().endsWith(path));
    }

    private static boolean supportsWorkspaceFolders(LanguageServerWrapper wrapper) {
        var workspace = wrapper.getServerCapabilitiesSync().getWorkspace();
        return workspace != null && workspace.getWorkspaceFolders() != null
                && Boolean.TRUE.equals(workspace.getWorkspaceFolders().getSupported());
    }

    private static MockWorkspaceService workspace() {
        return MockLanguageServer.INSTANCE.getWorkspaceService();
    }

    private static void assertRename(RenameFilesParams params, String oldName, String newName) {
        FileRename rename = params.getFiles().get(0);
        assertTrue(rename.getOldUri(), rename.getOldUri().endsWith(oldName));
        assertTrue(rename.getNewUri(), rename.getNewUri().endsWith(newName));
    }

    private LanguageServerWrapper connect(VirtualFile file) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null)
                .get(30, TimeUnit.SECONDS)
                .get(0)
                .getServerWrapper();
    }

    private static void register(Registration... registrations) throws Exception {
        await(MockLanguageServer.INSTANCE.getRemoteProxies().get(0)
                .registerCapability(new RegistrationParams(List.of(registrations))));
    }

    private static void unregister(Unregistration... unregistrations) throws Exception {
        await(MockLanguageServer.INSTANCE.getRemoteProxies().get(0)
                .unregisterCapability(new UnregistrationParams(List.of(unregistrations))));
    }

    private static void await(CompletableFuture<?> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("the client must answer", future::isDone, 10);
        future.get();
    }

    /**
     * Sends a notification and waits for the server to receive it: the requests and notifications sent before it have
     * then arrived, since they keep their order.
     */
    private void awaitTheNotificationsSentSoFar(LanguageServerWrapper wrapper) {
        var marker = new JsonObject();
        marker.addProperty("marker", "marker-" + markers.incrementAndGet());
        wrapper.sendNotification(ls -> {
            ls.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(marker));
            return ls;
        });
        PlatformTestUtil.waitWithEventsDispatching("the server must receive the later notification",
                () -> workspace().getConfigurationChanges().stream()
                        .anyMatch(change -> marker.equals(change.getSettings())), 10);
    }

    private void rename(VirtualFile file, String name) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            try {
                file.rename(this, name);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void delete(VirtualFile file) {
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
            try {
                file.delete(this);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}

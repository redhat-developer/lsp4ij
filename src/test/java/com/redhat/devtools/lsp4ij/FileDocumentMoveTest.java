/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.mock.MockTextDocumentService.OpenCloseNotification;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileDocumentMoveTest extends LSPCodeInsightFixtureTestCase {
    public FileDocumentMoveTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var operations = new FileOperationsServerCapabilities();
            operations.setDidRename(new FileOperationOptions(List.of(
                    new FileOperationFilter(new FileOperationPattern("**/*.foo")))));
            var workspace = new WorkspaceServerCapabilities();
            workspace.setFileOperations(operations);
            capabilities.setWorkspace(workspace);
            return capabilities;
        });
    }

    public void testNativeRenameClosesOldUriAndReopensUnsavedText() throws Exception {
        var context = openDocument();
        reconnectAfter(context, () -> write(() -> context.file().rename(this, "renamed.foo")));
    }

    public void testNativeMoveUndoAndRedoReconnectTheCurrentUri() throws Exception {
        var context = openDocument();
        var originalParent = context.file().getParent();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        reconnectAfter(context, () -> write(() -> context.file().move(this, destination)));
        var undo = UndoManager.getInstance(myFixture.getProject());
        assertTrue(undo.isUndoAvailable(null));
        reconnectAfter(context, () -> boundedOperation(() -> undo.undo(null)));
        assertEquals(originalParent, context.file().getParent());
        assertTrue(undo.isRedoAvailable(null));
        reconnectAfter(context, () -> boundedOperation(() -> undo.redo(null)));
        assertEquals(destination, context.file().getParent());
    }

    public void testARenameReconnectsTheFileOfALanguageServerWithoutFileOperations() throws Exception {
        MockLanguageServer.reset();
        var context = openDocument();
        reconnectAfter(context, () -> write(() -> context.file().rename(this, "renamed.foo")), false);
    }

    public void testAMoveReconnectsTheFileOfALanguageServerWithoutFileOperations() throws Exception {
        MockLanguageServer.reset();
        var context = openDocument();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        reconnectAfter(context, () -> write(() -> context.file().move(this, destination)), false);
    }

    public void testRenameTwiceBeforeReconnectOpensOnlyTheFinalUri() throws Exception {
        var context = openDocument();
        var reopened = new CompletableFuture<DidOpenTextDocumentParams>();
        expectDidOpenOfAnotherUri(reopened, context.wrapper().toUri(context.file()));
        write(() -> {
            context.file().rename(this, "intermediate.foo");
            context.file().rename(this, "final.foo");
        });
        PlatformTestUtil.waitWithEventsDispatching("the final URI must reconnect", reopened::isDone, 10);
        assertEquals(context.wrapper().toUri(context.file()), URI.create(reopened.join().getTextDocument().getUri()));
        assertEquals("unsaved", reopened.join().getTextDocument().getText());
        assertEquals(1, context.wrapper().getOpenedDocuments().size());
    }

    public void testCloseBeforeDeferredReconnectDoesNotReopenTheFile() throws Exception {
        var context = openDocument();
        write(() -> {
            context.file().rename(this, "renamed.foo");
            FileEditorManager.getInstance(myFixture.getProject()).closeFile(context.file());
        });
        // The move matches the file again after its write action, in a read action
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertTrue(context.wrapper().getOpenedDocuments().isEmpty());
    }

    public void testEditorOutsideFileEditorManagerRetainsItsConnectionAfterRename() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/raw.foo", "disk");
        var document = LSPIJUtils.getDocument(file);
        var editor = EditorFactory.getInstance().createEditor(document, myFixture.getProject());
        try {
            assertFalse(FileEditorManager.getInstance(myFixture.getProject()).isFileOpen(file));
            var context = connect(file);
            write(() -> document.setText("unsaved"));
            reconnectAfter(context, () -> write(() -> file.rename(this, "renamed.foo")));
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testADocumentRegisteredAsItsFileMovesIsTakenAlongWithoutFileOperations() throws Exception {
        // Without file operations, the move sees the document of a file only if it was open as the events were filtered
        MockLanguageServer.reset();
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        var earlierUri = wrapper.toUri(file);
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            var hold = holdRegistrationUntilMoved(wrapper);
            var servers = connectLater(file);
            PlatformTestUtil.waitWithEventsDispatching("The connection must check the place of the file", hold.holding::get, 10);

            hold.beforeMove();
            write(() -> file.move(this, destination));

            PlatformTestUtil.waitWithEventsDispatching("The document must be connected at the new place of its file",
                    () -> servers.isDone() && wrapper.isSynchronizing(file), 10);
            assertFalse("The document must not stay at the earlier place of its file", wrapper.isConnectedTo(earlierUri));
        } finally {
            wrapper.setBeforeRegistration(null);
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testAnOpenFileWhoseConnectionAMoveDropsIsConnectedAtItsNewPlace() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        wrapper.setConnectionExecutor(waiting::add);
        try {
            var servers = connectLater(file);
            PlatformTestUtil.waitWithEventsDispatching("The connection must wait", () -> !waiting.isEmpty(), 10);

            // The language server has no document of the file yet, so the move takes none along, and the connection
            // then sees that its file moved
            write(() -> file.move(this, destination));
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            waiting.forEach(AppExecutorUtil.getAppExecutorService()::execute);

            PlatformTestUtil.waitWithEventsDispatching("The open file must be connected at its new place",
                    () -> servers.isDone() && wrapper.isSynchronizing(file), 10);
        } finally {
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testAMovedFileDoesNotStartALanguageServerThatWasSwitchedOffMeanwhile() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        wrapper.setConnectionExecutor(waiting::add);
        try {
            var servers = connectLater(file);
            PlatformTestUtil.waitWithEventsDispatching("The connection must wait", () -> !waiting.isEmpty(), 10);
            write(() -> file.move(this, destination));
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            waiting.forEach(AppExecutorUtil.getAppExecutorService()::execute);
            // The connection drops the document of the moved file and hands the open file to the UI thread, which
            // this test holds until the language server is switched off
            servers.get(10, TimeUnit.SECONDS);
            wrapper.stopAndDisable();

            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            // Which then matches the file again in a read action
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertFalse("A language server that was switched off must not start again for a moved file", wrapper.isActive());
        } finally {
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testAMoveDoesNotWaitForTheDocumentsOfALanguageServerThatIsNotRunning() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        wrapper.stop().get(10, TimeUnit.SECONDS);
        assertFalse(wrapper.isActive());
        var documentsLock = documentsLock(wrapper);
        var held = new CountDownLatch(1);
        var released = new CountDownLatch(1);
        var waitedFor = new AtomicBoolean();
        // As a stop holds the lock while it disconnects the documents
        var holder = AppExecutorUtil.getAppExecutorService().submit(() -> {
            synchronized (documentsLock) {
                held.countDown();
                try {
                    waitedFor.set(!released.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        assertTrue(held.await(10, TimeUnit.SECONDS));

        WriteCommandAction.writeCommandAction(myFixture.getProject()).run(() -> file.rename(this, "renamed.foo"));
        released.countDown();

        holder.get(10, TimeUnit.SECONDS);
        assertFalse("The move must not wait for the documents of a language server that isn't running", waitedFor.get());
    }

    public void testARenameDoesNotConnectTheFileAgainToALanguageServerThatNoLongerMatchesIt() throws Exception {
        var context = openDocument();
        var earlierUri = context.wrapper().toUri(context.file());
        // A connection then opens the document on the thread that asks for it, so that it shows once that thread is done
        context.wrapper().setConnectionExecutor(Runnable::run);
        try {
            write(() -> context.file().rename(this, "test.txt"));

            // The move connects the file again after its write action
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertFalse(context.wrapper().isConnectedTo(earlierUri));
            assertFalse("A language server that no longer matches the renamed file must not get it again",
                    context.wrapper().isSynchronizing(context.file()));
            var psiFile = PsiManager.getInstance(myFixture.getProject()).findFile(context.file());
            assertNotNull(psiFile);
            assertEmpty("No language server matches the renamed file", LanguageServiceAccessor.getInstance(myFixture.getProject())
                    .getLanguageServers(psiFile, null, null).get(5, TimeUnit.SECONDS));
        } finally {
            context.wrapper().setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
        }
    }

    public void testAMovedDocumentIsConnectedAgainOnceAnAsynchronousMatcherAcceptsItsNewPlace() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var matcher = matchAsynchronously(wrapper.getServerDefinition());
        var file = myFixture.getTempDirFixture().createFile("source/test.async", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            assertSame(wrapper, connect(file).wrapper());
            var answer = new CompletableFuture<Boolean>();
            matcher.answers.put("kept.async", answer);

            write(() -> file.rename(this, "kept.async"));

            PlatformTestUtil.waitWithEventsDispatching("The move must ask the matcher about the new place of the file",
                    () -> matcher.asked.contains("kept.async"), 10);
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertFalse("The document must wait for the matcher", wrapper.isSynchronizing(file));
            answer.complete(true);
            PlatformTestUtil.waitWithEventsDispatching("The document must be connected once the matcher accepts its new place",
                    () -> wrapper.isSynchronizing(file), 10);
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testADocumentThatMovesAgainAsItsNewPlaceIsMatchedIsMatchedAgainAtItsLastPlace() throws Exception {
        var wrapper = connect(myFixture.getTempDirFixture().createFile("source/other.foo", "other")).wrapper();
        var matcher = matchAsynchronously(wrapper.getServerDefinition());
        var file = myFixture.getTempDirFixture().createFile("source/test.async", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            assertSame(wrapper, connect(file).wrapper());
            var answer = new CompletableFuture<Boolean>();
            matcher.answers.put("kept.async", answer);
            matcher.answers.put("rejected.async", CompletableFuture.completedFuture(false));

            write(() -> file.rename(this, "kept.async"));
            PlatformTestUtil.waitWithEventsDispatching("The move must ask the matcher about the new place of the file",
                    () -> matcher.asked.contains("kept.async"), 10);
            // The document is not connected while the matcher answers, so this move finds no document of the file
            write(() -> file.rename(this, "rejected.async"));
            answer.complete(true);

            PlatformTestUtil.waitWithEventsDispatching("The last place of the document must be matched again",
                    () -> matcher.asked.contains("rejected.async"), 10);
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertFalse("A match for a place that the document left must not connect it", wrapper.isSynchronizing(file));
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testARenameDoesNotConnectTheFileAgainToALanguageServerWhoseClientFeaturesLeaveItOut() throws Exception {
        // A language server of its own, whose client features leave out the files whose name starts with "off"
        Set<String> asked = ConcurrentHashMap.newKeySet();
        var definition = new MockLanguageServerDefinition("leaves-out-off", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures() {
                    @Override
                    public boolean isEnabled(@NotNull VirtualFile file) {
                        asked.add(file.getName());
                        return super.isEnabled(file) && !file.getName().startsWith("off");
                    }
                };
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.bar"), null)));
        var file = myFixture.getTempDirFixture().createFile("source/on.bar", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            var wrapper = connect(file).wrapper();
            assertSame(definition, wrapper.getServerDefinition());
            // A connection then opens the document on the thread that asks for it, so that it shows once that thread is done
            wrapper.setConnectionExecutor(Runnable::run);

            write(() -> file.rename(this, "off.bar"));

            PlatformTestUtil.waitWithEventsDispatching("The move must ask the client features about the renamed file",
                    () -> asked.contains("off.bar"), 10);
            assertFalse("A language server whose client features leave out the renamed file must not get it again",
                    wrapper.isSynchronizing(file));
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
        }
    }

    public void testAnOperationKeepsTheDocumentThatItConnectedInAScopeAfterTheEditorOfTheMovedFileClosed()
            throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        var released = false;
        var scope = new DocumentConnectionScope();
        var wrapper = connect(file).wrapper();
        Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        wrapper.setConnectionExecutor(waiting::add);
        try {
            write(() -> file.rename(this, "renamed.foo"));
            PlatformTestUtil.waitWithEventsDispatching("The move must connect the file again", () -> !waiting.isEmpty(), 10);
            var reconnection = waiting.poll();
            EditorFactory.getInstance().releaseEditor(editor);
            released = true;

            // An operation connects the file in a scope before the connection of the move ends
            var operation = LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServersForScope(
                    PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null, scope);
            PlatformTestUtil.waitWithEventsDispatching("The operation must connect the file", () -> !waiting.isEmpty(), 10);
            waiting.poll().run();
            assertEquals(1, operation.get(10, TimeUnit.SECONDS).size());
            // The connection of the move then finds the document open, and ends at once
            reconnection.run();
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

            var uri = wrapper.toUri(file);
            assertTrue("The operation must keep its document while its scope holds it", wrapper.isConnectedTo(uri));
            scope.close();
            PlatformTestUtil.waitWithEventsDispatching("The end of the scope must disconnect the document",
                    () -> !wrapper.isConnectedTo(uri), 10);
        } finally {
            scope.close();
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            if (!released) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
        }
    }

    public void testALanguageServerStopsOnceItsLastDocumentIsRenamedToANameThatItDoesNotMatch() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            var wrapper = connect(file).wrapper();
            assertTrue(wrapper.isActive());

            write(() -> file.rename(this, "test.txt"));

            // As when the last editor of its last document closes
            PlatformTestUtil.waitWithEventsDispatching("The language server must stop, as it has no document left",
                    () -> !wrapper.isActive(), 10);
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }

    public void testALanguageServerStopsOnceItsClientFeaturesLeaveOutItsLastDocumentAtTheNewPlaceOfItsFile()
            throws Exception {
        var definition = new MockLanguageServerDefinition("leaves-out-off", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures() {
                    @Override
                    public boolean isEnabled(@NotNull VirtualFile file) {
                        return super.isEnabled(file) && !file.getName().startsWith("off");
                    }
                };
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.bar"), null)));
        var file = myFixture.getTempDirFixture().createFile("source/on.bar", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        try {
            var wrapper = connect(file).wrapper();
            assertTrue(wrapper.isActive());

            write(() -> file.rename(this, "off.bar"));

            PlatformTestUtil.waitWithEventsDispatching("The language server must stop, as it has no document left",
                    () -> !wrapper.isActive(), 10);
        } finally {
            EditorFactory.getInstance().releaseEditor(editor);
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
        }
    }

    public void testALanguageServerStopsOnceTheEditorOfItsLastDocumentClosesAsTheMoveConnectsItAgain() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        var editor = EditorFactory.getInstance().createEditor(LSPIJUtils.getDocument(file), myFixture.getProject());
        var released = false;
        var wrapper = connect(file).wrapper();
        Queue<Runnable> waiting = new ConcurrentLinkedQueue<>();
        wrapper.setConnectionExecutor(waiting::add);
        try {
            write(() -> file.rename(this, "renamed.foo"));
            PlatformTestUtil.waitWithEventsDispatching("The move must connect the file again", () -> !waiting.isEmpty(), 10);
            EditorFactory.getInstance().releaseEditor(editor);
            released = true;
            waiting.poll().run();

            PlatformTestUtil.waitWithEventsDispatching("The language server must stop, as it has no document left",
                    () -> !wrapper.isActive(), 10);
        } finally {
            wrapper.setConnectionExecutor(AppExecutorUtil.getAppExecutorService());
            if (!released) {
                EditorFactory.getInstance().releaseEditor(editor);
            }
        }
    }

    /**
     * A document matcher that answers later: with the future given for the name of the file, and with a match
     * otherwise. It keeps the names of the files that it was asked about.
     */
    private static final class MatcherThatAnswersLater implements DocumentMatcher {
        private final Map<String, CompletableFuture<Boolean>> answers = new ConcurrentHashMap<>();
        private final Set<String> asked = ConcurrentHashMap.newKeySet();

        @Override
        public boolean match(@NotNull VirtualFile file, @NotNull Project project) {
            throw new AssertionError("An asynchronous matcher must be asked with matchAsync");
        }

        @Override
        public boolean shouldBeMatchedAsynchronously(@NotNull Project project) {
            return true;
        }

        @Override
        public @NotNull CompletableFuture<Boolean> matchAsync(@NotNull VirtualFile file, @NotNull Project project) {
            asked.add(file.getName());
            return answers.getOrDefault(file.getName(), CompletableFuture.completedFuture(true));
        }
    }

    /**
     * Maps the *.async files to the given definition with a matcher that answers later.
     */
    private static MatcherThatAnswersLater matchAsynchronously(@NotNull LanguageServerDefinition definition) {
        var matcher = new MatcherThatAnswersLater();
        LanguageServersRegistry.getInstance().registerAssociation(definition,
                new ServerFileNamePatternMapping(List.of("*.async"), definition.getId(), null, matcher));
        return matcher;
    }

    private static Object documentsLock(LanguageServerWrapper wrapper) throws ReflectiveOperationException {
        var field = LanguageServerWrapper.class.getDeclaredField("documentsLock");
        field.setAccessible(true);
        return field.get(wrapper);
    }

    /**
     * Holds the next registration of a document, once its connection has checked the place of its file, until a move
     * that the hold is told of changed the parent of a file. The hold reads nothing of the VFS: a thread that reads the
     * parent of a file without read access as the file moves can make the file keep its earlier parent. A move raises
     * the structure modification count of the VFS once it changed the parent, before any listener hears of the move.
     */
    static RegistrationHold holdRegistrationUntilMoved(LanguageServerWrapper wrapper) {
        var hold = new RegistrationHold();
        wrapper.setBeforeRegistration(() -> {
            if (hold.holding.compareAndSet(false, true)) {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!hold.moved() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
            }
        });
        return hold;
    }

    static final class RegistrationHold {

        final AtomicBoolean holding = new AtomicBoolean();
        private volatile boolean moving;
        private volatile int countBeforeMove;

        /**
         * Tells the hold of the move that ends it, right before the move.
         */
        void beforeMove() {
            countBeforeMove = ManagingFS.getInstance().getStructureModificationCount();
            moving = true;
        }

        private boolean moved() {
            return moving && ManagingFS.getInstance().getStructureModificationCount() != countBeforeMove;
        }
    }

    private CompletableFuture<List<LanguageServerItem>> connectLater(VirtualFile file) {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null);
    }

    private record Context(VirtualFile file, LanguageServerWrapper wrapper) {}

    private Context openDocument() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("source/test.foo", "disk");
        myFixture.configureFromExistingVirtualFile(file);
        var context = connect(file);
        write(() -> myFixture.getEditor().getDocument().setText("unsaved"));
        return context;
    }

    private Context connect(VirtualFile file) throws Exception {
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null)
                .get(5, TimeUnit.SECONDS);
        return new Context(file, servers.get(0).getServerWrapper());
    }

    /**
     * Expects the next didOpen of a URI other than the given one. The servers are returned once the didOpen of
     * the file is sent, and the mock server can read it after the expectation is set.
     */
    private static void expectDidOpenOfAnotherUri(CompletableFuture<DidOpenTextDocumentParams> opened, URI uri) {
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidOpenCallback(opened,
                params -> !uri.equals(URI.create(params.getTextDocument().getUri())));
    }

    private void reconnectAfter(Context context, Runnable operation) {
        reconnectAfter(context, operation, true);
    }

    private void reconnectAfter(Context context, Runnable operation, boolean didRenameFiles) {
        var closed = new CompletableFuture<DidCloseTextDocumentParams>();
        var opened = new CompletableFuture<DidOpenTextDocumentParams>();
        var renamed = new CompletableFuture<RenameFilesParams>();
        var closedBeforeOpen = opened.thenApply(ignored -> closed.isDone());
        URI oldUri = context.wrapper().toUri(context.file());
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidCloseCallback(closed);
        expectDidOpenOfAnotherUri(opened, oldUri);
        MockLanguageServer.INSTANCE.getWorkspaceService().setDidRenameCallback(renamed);
        operation.run();
        PlatformTestUtil.waitWithEventsDispatching("rename must deliver close, open and workspace notification",
                () -> closed.isDone() && opened.isDone() && (renamed.isDone() || !didRenameFiles) && closedBeforeOpen.isDone(), 10);
        URI newUri = context.wrapper().toUri(context.file());
        assertTrue("didClose must precede didOpen", closedBeforeOpen.join());
        assertEquals(oldUri, URI.create(closed.join().getTextDocument().getUri()));
        assertEquals(newUri, URI.create(opened.join().getTextDocument().getUri()));
        // The server reads the notifications in the order they were sent. Up to the reopen, each didOpen of the old
        // URI must be matched by a didClose: a late didOpen of the connection is, a reopen of the old URI is not.
        var read = List.copyOf(MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications());
        var beforeReopen = read.subList(0, read.lastIndexOf(new OpenCloseNotification(true, newUri)));
        assertEquals("the old URI must not be reopened",
                Collections.frequency(beforeReopen, new OpenCloseNotification(false, oldUri)),
                Collections.frequency(beforeReopen, new OpenCloseNotification(true, oldUri)));
        assertEquals("unsaved", opened.join().getTextDocument().getText());
        if (didRenameFiles) {
            assertEquals(1, renamed.join().getFiles().size());
            assertEquals(oldUri, URI.create(renamed.join().getFiles().get(0).getOldUri()));
            assertEquals(newUri, URI.create(renamed.join().getFiles().get(0).getNewUri()));
        } else {
            assertFalse("A language server without file operations must not get workspace/didRenameFiles",
                    renamed.isDone());
        }
        assertEquals(1, context.wrapper().getOpenedDocuments().size());
        assertNotNull(context.wrapper().getOpenedDocument(context.wrapper().toUri(context.file())));
    }

    private void write(ThrowableRunnable<IOException> operation) {
        boundedOperation(() -> {
            try {
                WriteCommandAction.writeCommandAction(myFixture.getProject())
                        .withName("Move file").withGlobalUndo().run(operation);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void boundedOperation(Runnable operation) {
        EdtTestUtil.runInEdtAndWait(() -> {
            var progress = new EmptyProgressIndicator();
            // The old implementation waits for a read action while holding the VFS write lock.
            // Cancel that wait on regression, so this test fails instead of hanging the suite.
            var deadline = AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(progress::cancel, 10, TimeUnit.SECONDS);
            try {
                ProgressManager.getInstance().runProcess(operation, progress);
                assertFalse("Native rename must not wait for didOpen under the write lock", progress.isCanceled());
            } finally {
                deadline.cancel(false);
            }
        });
    }
}

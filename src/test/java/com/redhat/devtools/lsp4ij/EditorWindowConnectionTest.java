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

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.SupervisorKt;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import javax.swing.SwingConstants;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A file open in an editor of any window or tab group stays connected to its language servers, which receive its
 * didOpen once and its didClose only when its last editor closes: also while the editor of a new tab group is still
 * being created, and when the platform closes an editor to open the file again in another window or tab group, as for
 * a tab dragged into a detached window.
 * <p>
 * The test editor manager has no windows, so the platform's own editor manager, with its tab groups, replaces it. A
 * detached window is a frame, which a headless test can't create. Its tab group, into which a drop opens the file, is
 * stood in for by another tab group of the main window, which the editor manager treats alike. Code vision is
 * disabled, as it asks for the servers of a file whose editor opens, which could connect the file again after the
 * close of its last editor.
 */
public class EditorWindowConnectionTest extends LSPCodeInsightFixtureTestCase {

    private boolean codeVisionEnabled;
    private Disposable editorManagerReplacement;
    private CoroutineScope editorManagerScope;
    private FileEditorManagerImpl editors;

    public EditorWindowConnectionTest() {
        super("*.ts");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        codeVisionEnabled = CodeVisionSettings.getInstance().getCodeVisionEnabled();
        CodeVisionSettings.getInstance().setCodeVisionEnabled(false);
        var project = myFixture.getProject();
        editorManagerReplacement = Disposer.newDisposable();
        editorManagerScope = CoroutineScopeKt.CoroutineScope(SupervisorKt.SupervisorJob(null));
        editors = new FileEditorManagerImpl(project, editorManagerScope);
        ServiceContainerUtil.replaceService(project, FileEditorManager.class, editors, editorManagerReplacement);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            editors.closeAllFiles();
            Disposer.dispose(editors);
            Disposer.dispose(editorManagerReplacement);
            CoroutineScopeKt.cancel(editorManagerScope, null);
            CodeVisionSettings.getInstance().setCodeVisionEnabled(codeVisionEnabled);
        } finally {
            super.tearDown();
        }
    }

    public void testAFileOpenInTwoTabGroupsStaysConnectedUntilItsLastEditorCloses() throws Exception {
        var file = openConnected("open-in-two-tab-groups.ts");
        var first = editors.getCurrentWindow();
        var second = first.split(SwingConstants.VERTICAL, true, file, false);
        assertNotNull("the split must open the file in a second tab group", second);

        // Closed right after the split, whose tab group the platform can still be creating its editor of the file for
        editors.closeFile(file, first);

        assertTrue("the file must stay open in the second tab group", second.isFileOpen(file));
        assertTheServerHasTheFileOpen(file);
        assertClosingTheLastEditorSendsOneDidClose(file, second);
    }

    public void testAFileWhoseEditorMovesToTheOppositeTabGroupStaysConnected() throws Exception {
        var file = openConnected("moved-to-opposite-tab-group.ts");
        var source = editors.getCurrentWindow();
        var target = source.split(SwingConstants.VERTICAL, true, otherFile(), false);
        assertNotNull("the split must open another file in a second tab group", target);

        // The platform's action closes the editor, marked as closed to be opened again, then opens the file in the
        // opposite tab group
        var action = ActionManager.getInstance().getAction("MoveEditorToOppositeTabGroup");
        var context = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, myFixture.getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .add(EditorWindow.DATA_KEY, source)
                .build();
        action.actionPerformed(TestActionEvent.createTestEvent(action, context));

        assertTrue("the editor must move to the opposite tab group", target.isFileOpen(file));
        assertTheServerHasTheFileOpen(file);
        assertClosingTheLastEditorSendsOneDidClose(file, target);
    }

    @SuppressWarnings("deprecation")
    public void testAFileWhoseEditorIsDraggedIntoAnotherWindowStaysConnected() throws Exception {
        var file = openConnected("dragged-into-another-window.ts");
        var source = editors.getCurrentWindow();
        var target = source.split(SwingConstants.VERTICAL, true, otherFile(), false);
        assertNotNull("the split must open another file in a second tab group", target);

        // As the drag of a tab into another window ends: it closes the editor, marked as closed to be opened again,
        // and the drop opens the file in the window only later, in a coroutine, as it does for a detached window
        file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, true);
        editors.closeFile(file, source);
        file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
        assertFalse("the file must be in no editor until the drop opens it", editors.isFileOpen(file));
        editors.openFileWithProviders(file, false, target);

        assertTrue("the drop must open the file in the other window", target.isFileOpen(file));
        assertTheServerHasTheFileOpen(file);
        assertClosingTheLastEditorSendsOneDidClose(file, target);
    }

    /**
     * Opens a new file in an editor and connects it as a feature of the editor does, once the server has read its
     * didOpen.
     */
    private VirtualFile openConnected(String fileName) {
        var psiFile = myFixture.addFileToProject(fileName, "const value=1;");
        var file = psiFile.getVirtualFile();
        editors.openFile(file, true);
        var servers = ReadAction.compute(() -> LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(psiFile, null, null));
        PlatformTestUtil.waitWithEventsDispatching("the file must be connected", servers::isDone, 10);
        PlatformTestUtil.waitWithEventsDispatching(() -> "the server must read the didOpen of the open file; it read "
                + openCloseNotificationsOf(file), () -> !openCloseNotificationsOf(file).isEmpty(), 10);
        assertEquals(List.of("didOpen"), openCloseNotificationsOf(file));
        return file;
    }

    /** A file that no language server serves, to give a second tab group an editor. */
    private VirtualFile otherFile() {
        return myFixture.addFileToProject("other.txt", "other").getVirtualFile();
    }

    /**
     * Asserts that the server has read the didOpen of the file and no didClose, once it has read all that was sent
     * until now: a probe sent after it reaches the server after it.
     */
    private void assertTheServerHasTheFileOpen(VirtualFile file) {
        assertTrue("the file must be open in an editor", editors.isFileOpen(file));
        var wrapper = ContainerUtil.getOnlyItem(LanguageServiceAccessor.getInstance(myFixture.getProject()).getStartedServers());
        assertNotNull("the server of the file must have started", wrapper);
        var probe = new CompletableFuture<DidSaveTextDocumentParams>();
        MockLanguageServer.INSTANCE.getTextDocumentService().setDidSaveCallback(probe);
        var uri = wrapper.toUri(file).toString();
        wrapper.sendNotification(server -> {
            server.getTextDocumentService().didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri)));
            return server;
        });
        PlatformTestUtil.waitWithEventsDispatching("the server must read the probe sent after the editor changes",
                probe::isDone, 10);
        assertEquals("the server must keep the file open while an editor of it remains",
                List.of("didOpen"), openCloseNotificationsOf(file));
    }

    private void assertClosingTheLastEditorSendsOneDidClose(VirtualFile file, EditorWindow window) {
        editors.closeFile(file, window);

        assertFalse("the closed editor must be the last of the file", editors.isFileOpen(file));
        PlatformTestUtil.waitWithEventsDispatching(() -> "closing the last editor must send the didClose; the server read "
                + openCloseNotificationsOf(file), () -> openCloseNotificationsOf(file).contains("didClose"), 10);
        assertEquals(List.of("didOpen", "didClose"), openCloseNotificationsOf(file));
    }

    /** The didOpen and didClose notifications of the file that the server read, in the order it read them. */
    private static List<String> openCloseNotificationsOf(VirtualFile file) {
        return MockLanguageServer.INSTANCE.getTextDocumentService().getOpenCloseNotifications().stream()
                .filter(notification -> notification.uri().getPath().endsWith("/" + file.getName()))
                .map(notification -> notification.open() ? "didOpen" : "didClose")
                .toList();
    }
}

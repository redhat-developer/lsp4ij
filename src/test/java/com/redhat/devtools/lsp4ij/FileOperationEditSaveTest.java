/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The edits a server returns for workspace/willRenameFiles are applied during the VFS event, where a document
 * cannot be saved. The documents without an editor they change are saved once the event is over, since a language
 * server reads those from disk, and before the server is told of the rename.
 */
public class FileOperationEditSaveTest extends LSPCodeInsightFixtureTestCase {

    private static final String OLD_TEXT = "class Box";
    private static final String NEW_TEXT = "class Parcel";

    public FileOperationEditSaveTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            var filters = new FileOperationOptions(List.of(new FileOperationFilter(new FileOperationPattern("**/*.foo"))));
            var operations = new FileOperationsServerCapabilities();
            operations.setWillRename(filters);
            operations.setDidRename(filters);
            var workspace = new WorkspaceServerCapabilities();
            workspace.setFileOperations(operations);
            capabilities.setWorkspace(workspace);
            return capabilities;
        });
    }

    public void testWillRenameEditToAFileWithoutAnEditorIsSaved() throws Exception {
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "text");
        var other = myFixture.getTempDirFixture().createFile("other.foo", OLD_TEXT);
        connect(renamed);
        answerWillRenameWithAnEditTo(other);

        rename(renamed, "moved.foo");

        PlatformTestUtil.waitWithEventsDispatching("the willRenameFiles edit must be applied",
                () -> NEW_TEXT.equals(FileDocumentManager.getInstance().getDocument(other).getText()), 10);
        PlatformTestUtil.waitWithEventsDispatching("the edited file without an editor must be saved",
                () -> !FileDocumentManager.getInstance().isDocumentUnsaved(FileDocumentManager.getInstance().getDocument(other)), 10);
        assertEquals(NEW_TEXT, VfsUtilCore.loadText(other));
    }

    public void testTheServerIsToldOfTheRenameAfterTheSave() throws Exception {
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "text");
        var other = myFixture.getTempDirFixture().createFile("other.foo", OLD_TEXT);
        connect(renamed);
        answerWillRenameWithAnEditTo(other);
        // What the server reads from disk when it is told of the rename
        var didRename = new CompletableFuture<RenameFilesParams>();
        CompletableFuture<String> textAtDidRename = didRename.thenApply(params -> loadText(other));
        MockLanguageServer.INSTANCE.getWorkspaceService().setDidRenameCallback(didRename);

        rename(renamed, "moved.foo");

        PlatformTestUtil.waitWithEventsDispatching("the server must be told of the rename", textAtDidRename::isDone, 10);
        assertEquals("the edited file must be saved before the server is told of the rename",
                NEW_TEXT, textAtDidRename.get());
    }

    private static void answerWillRenameWithAnEditTo(VirtualFile file) {
        MockLanguageServer.INSTANCE.getWorkspaceService().setWillRename(new WorkspaceEdit(Map.of(
                LSPIJUtils.toUri(file).toString(),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, OLD_TEXT.length())), NEW_TEXT)))));
    }

    private static String loadText(VirtualFile file) {
        try {
            return VfsUtilCore.loadText(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void connect(VirtualFile file) throws Exception {
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null)
                .get(5, TimeUnit.SECONDS);
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
}

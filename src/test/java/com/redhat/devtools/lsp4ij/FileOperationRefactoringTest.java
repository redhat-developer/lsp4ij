/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.RenameProcessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IntelliJ makes the renames and moves of a refactoring in a write action, which blocks the UI. A rename or move
 * refactoring therefore sends workspace/willRenameFiles before its write action, waits for the edit under its
 * progress, and applies the edit when it renames the files, without sending the request again.
 */
public class FileOperationRefactoringTest extends LSPCodeInsightFixtureTestCase {

    private static final String OLD_TEXT = "class Box";
    private static final String NEW_TEXT = "class Parcel";

    // The write actions running now
    private final AtomicInteger writeActions = new AtomicInteger();

    public FileOperationRefactoringTest() {
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

    public void testRenameRefactoringAsksTheServerBeforeItsWriteAction() throws Exception {
        var renamed = myFixture.getTempDirFixture().createFile("renamed.foo", "text");
        var other = myFixture.getTempDirFixture().createFile("other.foo", OLD_TEXT);
        connect(renamed);
        answerWillRenameSlowlyWithAnEditTo(other);
        var askedInWriteAction = whetherTheServerIsAskedInAWriteAction();

        new RenameProcessor(myFixture.getProject(), psiFile(renamed), "moved.foo", false, false).run();

        assertFalse("workspace/willRenameFiles must be sent before the write action of the refactoring",
                askedInWriteAction.get(10, TimeUnit.SECONDS));
        var requests = MockLanguageServer.INSTANCE.getWorkspaceService().getWillRenameRequests();
        assertEquals("the rename must send workspace/willRenameFiles once", 1, requests.size());
        var fileRename = requests.get(0).getFiles().get(0);
        assertTrue(fileRename.getOldUri(), fileRename.getOldUri().endsWith("/renamed.foo"));
        assertTrue(fileRename.getNewUri(), fileRename.getNewUri().endsWith("/moved.foo"));
        assertEquals("moved.foo", renamed.getName());
        assertEquals("the willRenameFiles edit must be applied once", NEW_TEXT, text(other));
    }

    public void testMoveRefactoringAsksTheServerOnceForAllMovedFilesBeforeItsWriteAction() throws Exception {
        var first = myFixture.getTempDirFixture().createFile("first.foo", "text");
        var second = myFixture.getTempDirFixture().createFile("second.foo", "text");
        var other = myFixture.getTempDirFixture().createFile("other.foo", OLD_TEXT);
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        connect(first);
        answerWillRenameSlowlyWithAnEditTo(other);
        var askedInWriteAction = whetherTheServerIsAskedInAWriteAction();

        new MoveFilesOrDirectoriesProcessor(myFixture.getProject(), new PsiElement[]{psiFile(first), psiFile(second)},
                psiDirectory(destination), false, false, null, null).run();

        assertFalse("workspace/willRenameFiles must be sent before the write action of the refactoring",
                askedInWriteAction.get(10, TimeUnit.SECONDS));
        var requests = MockLanguageServer.INSTANCE.getWorkspaceService().getWillRenameRequests();
        assertEquals("the move must send workspace/willRenameFiles once", 1, requests.size());
        assertEquals("the request must contain both moved files", 2, requests.get(0).getFiles().size());
        assertEquals(destination, first.getParent());
        assertEquals(destination, second.getParent());
        assertEquals("the willRenameFiles edit must be applied once", NEW_TEXT, text(other));
    }

    /**
     * The server answers after a delay, to check that the refactoring waits for the edit.
     */
    private static void answerWillRenameSlowlyWithAnEditTo(VirtualFile file) {
        var workspaceService = MockLanguageServer.INSTANCE.getWorkspaceService();
        workspaceService.setWillRename(new WorkspaceEdit(Map.of(
                LSPIJUtils.toUri(file).toString(),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, OLD_TEXT.length())), NEW_TEXT)))));
        workspaceService.setWillRenameDelayMs(500);
    }

    /**
     * @return whether a write action was running when the server received the first workspace/willRenameFiles request
     */
    private CompletableFuture<Boolean> whetherTheServerIsAskedInAWriteAction() {
        var willRename = new CompletableFuture<RenameFilesParams>();
        // Runs when the server receives the request
        var askedInWriteAction = willRename.thenApply(params -> writeActions.get() > 0);
        MockLanguageServer.INSTANCE.getWorkspaceService().setWillRenameCallback(willRename);
        return askedInWriteAction;
    }

    private static String text(VirtualFile file) {
        return FileDocumentManager.getInstance().getDocument(file).getText();
    }

    private PsiFile psiFile(VirtualFile file) {
        return PsiManager.getInstance(myFixture.getProject()).findFile(file);
    }

    private PsiDirectory psiDirectory(VirtualFile directory) {
        return PsiManager.getInstance(myFixture.getProject()).findDirectory(directory);
    }

    private void connect(VirtualFile file) throws Exception {
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(psiFile(file), null, null)
                .get(5, TimeUnit.SECONDS);
    }
}

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
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.RenameFileOptions;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A RenameFile operation of a workspace edit moves the resource to the directory of its new URI and gives it the
 * name of that URI, and honors its overwrite and ignoreIfExists options. A rename that cannot be applied stops the
 * edit, whose later changes can address the new URI, and workspace/applyEdit then answers that it was not applied.
 */
public class WorkspaceResourceRenameTest extends LSPCodeInsightFixtureTestCase {

    public WorkspaceResourceRenameTest() {
        super("*.foo");
    }

    public void testResourceRenameMovesToTheRequestedParentAndName() throws Exception {
        var file = myFixture.addFileToProject("source/old.txt", "content").getVirtualFile();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination space");
        apply(rename(file, destination.toNioPath().resolve("new #%.txt").toUri().toString()));
        assertEquals(destination, file.getParent());
        assertEquals("new #%.txt", file.getName());
    }

    public void testDirectoryMovePreservesChildren() throws Exception {
        var child = myFixture.addFileToProject("source/folder/child.txt", "content").getVirtualFile();
        var directory = child.getParent();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        apply(rename(directory, destination.toNioPath().resolve("renamed").toUri().toString()));
        assertEquals(destination, directory.getParent());
        assertEquals("renamed", directory.getName());
        assertEquals(child, directory.findChild("child.txt"));
    }

    public void testMissingDestinationDirectoryIsCreated() {
        var file = myFixture.addFileToProject("source/old.txt", "content").getVirtualFile();
        var root = file.getParent().getParent();
        apply(rename(file, root.toNioPath().resolve("created/nested/new.txt").toUri().toString()));
        assertEquals("new.txt", file.getName());
        assertEquals(root.findFileByRelativePath("created/nested"), file.getParent());
    }

    public void testCaseOnlyRenameRenamesTheResource() {
        var file = myFixture.addFileToProject("source/name.txt", "content").getVirtualFile();
        apply(rename(file, file.getParent().toNioPath().resolve("Name.txt").toUri().toString()));
        assertEquals("Name.txt", file.getName());
    }

    public void testCaseOnlyDirectoryRenameRenamesTheDirectory() {
        var directory = myFixture.addFileToProject("source/folder/child.txt", "content").getVirtualFile().getParent();
        apply(rename(directory, directory.getParent().toNioPath().resolve("Folder").toUri().toString()));
        assertEquals("Folder", directory.getName());
    }

    public void testRenamingADirectoryToItsOwnUriChangesNothing() {
        var directory = myFixture.addFileToProject("source/folder/child.txt", "content").getVirtualFile().getParent();
        var parent = directory.getParent();
        assertNull(apply(rename(directory, directory.toNioPath().toUri().toString())));
        assertEquals(parent, directory.getParent());
        assertEquals("folder", directory.getName());
    }

    public void testAnEditAfterAMoveReachesTheMovedFile() throws Exception {
        var file = myFixture.addFileToProject("source/old.txt", "content").getVirtualFile();
        var destination = myFixture.getTempDirFixture().findOrCreateDir("destination");
        String newUri = destination.toNioPath().resolve("new.txt").toUri().toString();
        apply(new WorkspaceEdit(List.of(Either.forRight(rename(file, newUri)), insert(newUri, "edited "))));
        assertEquals(destination, file.getParent());
        assertEquals("edited content", text(file));
    }

    public void testIgnoreExistingDestinationDoesNotMoveSource() {
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        var rename = rename(source, target.toNioPath().toUri().toString());
        rename.setOptions(new RenameFileOptions(false, true));
        apply(rename);
        assertTrue(source.isValid());
        assertEquals("source.txt", source.getName());
        assertTrue(target.isValid());
    }

    public void testOverwriteReplacesTheExistingDestination() {
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        var rename = rename(source, target.toNioPath().toUri().toString());
        // overwrite wins over ignoreIfExists
        rename.setOptions(new RenameFileOptions(true, true));
        apply(rename);
        assertFalse(target.isValid());
        assertEquals("target.txt", source.getName());
        assertEquals("source", text(source));
    }

    public void testIntermediateCollisionUsesSafeOperationOrder() {
        var source = myFixture.addFileToProject("source/old.txt", "source").getVirtualFile();
        var unrelated = myFixture.addFileToProject("destination/old.txt", "other").getVirtualFile();
        var target = unrelated.getParent();
        apply(rename(source, target.toNioPath().resolve("new.txt").toUri().toString()));
        assertEquals(target, source.getParent());
        assertEquals("new.txt", source.getName());
        assertTrue(unrelated.isValid());
    }

    public void testOccupiedDestinationRefusesWithoutRenamingSource() {
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        String failure = apply(rename(source, target.toNioPath().toUri().toString()));
        assertNotNull("Existing destinations require explicit overwrite", failure);
        assertTrue(failure, failure.contains("already exists"));
        assertTrue(source.isValid());
        assertEquals("source.txt", source.getName());
        assertTrue(target.isValid());
    }

    public void testARefusedRenameStopsTheEditBeforeItChangesTheFileAtTheNewUri() {
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        String targetUri = target.toNioPath().toUri().toString();
        assertNotNull(apply(new WorkspaceEdit(List.of(Either.forRight(rename(source, targetUri)), insert(targetUri, "edited ")))));
        assertEquals("the edit meant for the renamed file must not reach the file that was already there",
                "target", text(target));
    }

    public void testTheChangesBeforeARefusedRenameAreSaved() throws Exception {
        var edited = myFixture.addFileToProject("edited.txt", "content").getVirtualFile();
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        assertNotNull(apply(new WorkspaceEdit(List.of(insert(edited.toNioPath().toUri().toString(), "edited "),
                Either.forRight(rename(source, target.toNioPath().toUri().toString()))))));
        assertEquals("a document without an editor is saved, so that the server reads the edit from disk",
                "edited content", VfsUtilCore.loadText(edited));
    }

    public void testWorkspaceApplyEditAnswersNotAppliedForARefusedRename() throws Exception {
        var file = myFixture.configureByText("main.foo", "");
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        var source = myFixture.addFileToProject("source.txt", "source").getVirtualFile();
        var target = myFixture.addFileToProject("target.txt", "target").getVirtualFile();
        var edit = new WorkspaceEdit(List.of(Either.forRight(rename(source, target.toNioPath().toUri().toString()))));
        var pending = MockLanguageServer.INSTANCE.getRemoteProxies().get(0).applyEdit(new ApplyWorkspaceEditParams(edit));
        PlatformTestUtil.waitWithEventsDispatching("workspace/applyEdit reply", pending::isDone, 30);
        ApplyWorkspaceEditResponse response = pending.join();
        assertFalse(response.isApplied());
        assertTrue(response.getFailureReason(), response.getFailureReason().contains("already exists"));
        assertEquals("source.txt", source.getName());
    }

    /** @return why the edit stopped, and null if it was applied */
    private String apply(RenameFile rename) {
        return apply(new WorkspaceEdit(List.of(Either.forRight(rename))));
    }

    private String apply(WorkspaceEdit edit) {
        return WriteCommandAction.writeCommandAction(myFixture.getProject())
                .compute(() -> LSPIJUtils.applyWorkspaceEdit(edit, null, true, new LinkedHashSet<>()));
    }

    private static RenameFile rename(VirtualFile file, String newUri) {
        return new RenameFile(file.toNioPath().toUri().toString(), newUri);
    }

    private static Either<TextDocumentEdit, ResourceOperation> insert(String uri, String text) {
        return Either.forLeft(new TextDocumentEdit(new VersionedTextDocumentIdentifier(uri, null),
                List.of(Either.forLeft(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), text)))));
    }

    private static String text(VirtualFile file) {
        return FileDocumentManager.getInstance().getDocument(file).getText();
    }
}

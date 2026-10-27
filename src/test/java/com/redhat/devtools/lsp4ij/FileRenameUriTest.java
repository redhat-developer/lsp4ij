/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Verifies the file URIs that workspace/willRenameFiles and workspace/didRenameFiles send for a file
 * name that is not a valid URI as it stands.
 */
public class FileRenameUriTest extends LSPCodeInsightFixtureTestCase {

    // A space, '#' and '%' must be escaped, and a ':' must not make the name a URI scheme.
    // Windows file names cannot contain ':'.
    private static final String NAME = SystemInfo.isWindows ? "a b #%.foo" : "a b #%:.foo";

    public FileRenameUriTest() {
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

    public void testWillRenameFilesSendsTheNewNameAsAFileUri() throws Exception {
        var file = myFixture.getTempDirFixture().createFile("plain.foo", "text");
        var wrapper = connect(file);
        URI oldUri = wrapper.toUri(file);
        var willRename = new CompletableFuture<RenameFilesParams>();
        MockLanguageServer.INSTANCE.getWorkspaceService().setWillRenameCallback(willRename);

        rename(file, NAME);

        PlatformTestUtil.waitWithEventsDispatching("workspace/willRenameFiles must be sent", willRename::isDone, 10);
        FileRename sent = willRename.join().getFiles().get(0);
        assertEquals(oldUri, URI.create(sent.getOldUri()));
        // The new URI must be the one the renamed file is then synchronized under.
        assertEquals(wrapper.toUri(file), URI.create(sent.getNewUri()));
    }

    public void testDidRenameFilesSendsTheOldNameAsAFileUri() throws Exception {
        var file = myFixture.getTempDirFixture().createFile(NAME, "text");
        var wrapper = connect(file);
        URI oldUri = wrapper.toUri(file);
        var didRename = new CompletableFuture<RenameFilesParams>();
        MockLanguageServer.INSTANCE.getWorkspaceService().setDidRenameCallback(didRename);

        rename(file, "plain.foo");

        PlatformTestUtil.waitWithEventsDispatching("workspace/didRenameFiles must be sent", didRename::isDone, 10);
        FileRename sent = didRename.join().getFiles().get(0);
        // The old URI must be the one the file was synchronized under before the rename.
        assertEquals(oldUri, URI.create(sent.getOldUri()));
        assertEquals(wrapper.toUri(file), URI.create(sent.getNewUri()));
    }

    private LanguageServerWrapper connect(VirtualFile file) throws Exception {
        var servers = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(PsiManager.getInstance(myFixture.getProject()).findFile(file), null, null)
                .get(5, TimeUnit.SECONDS);
        return servers.get(0).getServerWrapper();
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

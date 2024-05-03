/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static junit.framework.TestCase.*;

/**
 * LSP4IJ Assert.
 */
public class LSP4IJAssert {

    public static void assertOffset(String content, int line, int character, int expectedOffset, Character expectedChar) {
        assertOffset(content, new Position(line, character), expectedOffset, expectedChar);
    }

    public static void assertOffset(String content, Position position, int expectedOffset, Character expectedChar) {
        Document document = new DocumentImpl(content);
        int actualOffset = LSPIJUtils.toOffset(position, document);
        assertEquals(expectedOffset, actualOffset);
        if (expectedChar == null) {
            assertTrue(expectedOffset == content.length()  /* offset is at the end of the file */
                    || content.charAt(expectedOffset) == '\n' /* or offset is at the end of line */);
        } else {
            // get the character from the given offset
            Character actualChar = document.getCharsSequence().charAt(expectedOffset);
            assertEquals(expectedChar, actualChar);
        }
    }

    public static void assertWordRangeAt(final String contentWithOffset, String expected) {
        int offset = contentWithOffset.indexOf('|');
        String content = contentWithOffset.substring(0, offset) + contentWithOffset.substring(offset + 1);
        Document document = new DocumentImpl(content);
        TextRange textRange = LSPIJUtils.getWordRangeAt(document, offset);
        if (expected == null) {
            assertNull("TextRange should be null",textRange);
            return;
        }

        assertNotNull("TextRange should not be null", textRange);

        String startPart = document.getText(new TextRange(0, textRange.getStartOffset()));
        String rangePart = document.getText(textRange);
        String endPart = document.getText(new TextRange(textRange.getEndOffset(), content.length()));
        String actual = startPart + "[" + rangePart + "]" + endPart;
        assertEquals(expected, actual);
    }

    public static void assertApplyWorkspaceEdit(Path filePath, String text, String json, String expected, Project project) throws IOException {
        initializeFileContent(filePath, text);
        applyWorkspaceEdit(json, project);
        assertFileContent(filePath, expected);
    }

    public static void assertApplyWorkspaceEdit(Path oldFilePath, Path newFilePath, String text, String json, String expected, Project project) throws IOException {
        initializeFileContent(oldFilePath, text);
        applyWorkspaceEdit(json, project);
        assertFileContent(newFilePath, expected);
    }

    private static void applyWorkspaceEdit(String json, Project project) {
        WorkspaceEdit workspaceEdit = JSONUtils.getLsp4jGson().fromJson(json, WorkspaceEdit.class);
        WriteCommandAction.runWriteCommandAction(project, () -> LSPIJUtils.applyWorkspaceEdit(workspaceEdit));
    }

    private static void initializeFileContent(Path filePath, String text) throws IOException {
        Files.writeString(filePath, text);
        assertFileContent(filePath, text);
    }

    private static void assertFileContent(Path filePath, String expected) {
        VirtualFile file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath);
        Document document = LSPIJUtils.getDocument(file);
        assertEquals(expected, document.getText());
    }
}

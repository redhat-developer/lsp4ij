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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertApplyWorkspaceEdit;

/**
 * Tests for {@link LSPIJUtils#applyWorkspaceEdit(WorkspaceEdit)} with Scala LS.
 */
public class LSPIJUtils_applyWorkspaceEdit_ScalaTest extends BasePlatformTestCase {

    public void testRenameObjectWithOneDocumentChanges() throws IOException {
        Path filePath = Files.createTempFile(null, ".scala");
        String content = """
                object Foo {}
                """;
        // Rename Foo --> Bar
        String json = """
                {
                   "changes": {},
                   "documentChanges": [
                     {
                       "textDocument": {
                         "version": null,
                         "uri": "%s"
                       },
                       "edits": [
                         {
                           "range": {
                             "start": {
                               "line": 0,
                               "character": 7
                             },
                             "end": {
                               "line": 0,
                               "character": 10
                             }
                           },
                           "newText": "Bar"
                         }
                       ]
                     }
                   ]
                 }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                object Bar {}
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected,getProject());
    }

}

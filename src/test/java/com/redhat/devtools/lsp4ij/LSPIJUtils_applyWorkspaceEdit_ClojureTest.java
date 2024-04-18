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
public class LSPIJUtils_applyWorkspaceEdit_ClojureTest extends BasePlatformTestCase {

    public void testRenamePackageWithRenameDocumentChanges() throws IOException {
        Path fileDir = Files.createTempDirectory("lsp4ij-clojure");
        String baseDir = fileDir.toUri().toASCIIString();
        String content = """
                (ns foo)
                """;
        // Rename foo.clj file --> bar.clj file
        String json = """
                {
                    "changes": {},
                    "documentChanges": [
                      {
                        "oldUri": "%s/foo.clj",
                        "newUri": "%s/bar.clj",
                        "kind": "rename"
                      }
                    ]
                  }
                """.formatted(baseDir, baseDir);
        String expected = """
                (ns foo)
                """;
        Path oldFilePath = fileDir.resolve("foo.clj");
        Path newFilePath = fileDir.resolve("bar.clj");
        assertApplyWorkspaceEdit(oldFilePath, newFilePath, content, json, expected,getProject());
    }

}

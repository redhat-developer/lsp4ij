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
 * Tests for {@link LSPIJUtils#applyWorkspaceEdit(WorkspaceEdit)} with TypeScript LS.
 */
public class LSPIJUtils_applyWorkspaceEdit_TypeScriptTest extends BasePlatformTestCase {

    public void testRenameFunctionWithOneChanges() throws IOException {
        Path filePath = Files.createTempFile(null, ".ts");
        String content = """
                function foo() {}
                """;
        // Rename foo --> bar
        String json = """
                {
                  "changes": {
                    "%s": [
                      {
                        "range": {
                          "start": {
                            "line": 0,
                            "character": 9
                          },
                          "end": {
                            "line": 0,
                            "character": 12
                          }
                        },
                        "newText": "bar"
                      }
                    ]
                  }
                }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                function bar() {}
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected,getProject());
    }

    public void testRenameFunctionWithTwoChanges() throws IOException {
        Path filePath = Files.createTempFile(null, ".ts");
        String content = """
                function foo() {}
                foo();
                """;

        // Rename foo --> bar
        String json = """
                {
                  "changes": {
                    "%s": [
                      {
                        "range": {
                          "start": {
                            "line": 0,
                            "character": 9
                          },
                          "end": {
                            "line": 0,
                            "character": 12
                          }
                        },
                        "newText": "bar"
                      },
                      {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 0
                          },
                          "end": {
                            "line": 1,
                            "character": 3
                          }
                        },
                        "newText": "bar"
                      }
                    ]
                  }
                }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                function bar() {}
                bar();
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected, getProject());
    }

}

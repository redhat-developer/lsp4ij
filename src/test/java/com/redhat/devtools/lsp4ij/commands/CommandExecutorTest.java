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
package com.redhat.devtools.lsp4ij.commands;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.eclipse.lsp4j.Command;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests for LSP {@link CommandExecutor}.
 */
public class CommandExecutorTest extends BasePlatformTestCase {

    public void testExecuteCommandWithInvalidTextEdit() throws IOException {
        String command = """
                {
                    "title": "2 implementations",
                    "command": "rust-analyzer.showReferences",
                    "arguments": [
                        "file:///c:/Users/azerr/git/pingora/pingora-limits/src/estimator.rs",
                        {
                            "line": 25,
                            "character": 11
                        },
                        [
                            {
                                "uri": "file:///c:/Users/azerr/git/pingora/pingora-limits/src/estimator.rs",
                                "range": {
                                    "start": {
                                        "line": 29,
                                        "character": 0
                                    },
                                    "end": {
                                        "line": 84,
                                        "character": 1
                                    }
                                }
                            },
                            {
                                "uri": "file:///c:/Users/azerr/git/pingora/pingora-limits/benches/benchmark.rs",
                                "range": {
                                    "start": {
                                        "line": 68,
                                        "character": 0
                                    },
                                    "end": {
                                        "line": 76,
                                        "character": 1
                                    }
                                }
                            }
                        ]
                    ]
                }
                """;
        assertExecuteCommand(command, false);
    }

    public void testExecuteCommandWithValidTextEdit() throws IOException {
        String command = """
                {
                    "title": "2 implementations",
                    "command": "rust-analyzer.showReferences",
                    "arguments": [
                        "file:///c:/Users/azerr/git/pingora/pingora-limits/src/estimator.ts",
                        {
                            "line": 25,
                            "character": 11
                        },
                        [
                            {
                                "uri": "file:///c:/Users/azerr/git/pingora/pingora-limits/src/estimator.ts",
                                "range": {
                                    "start": {
                                        "line": 29,
                                        "character": 0
                                    },
                                    "end": {
                                        "line": 84,
                                        "character": 1
                                    }
                                },
                                "newText": "foo"
                            },
                            {
                                "uri": "file:///c:/Users/azerr/git/pingora/pingora-limits/benches/benchmark.ts",
                                "range": {
                                    "start": {
                                        "line": 68,
                                        "character": 0
                                    },
                                    "end": {
                                        "line": 76,
                                        "character": 1
                                    }
                                }
                            }
                        ]
                    ]
                }
                """;
        assertExecuteCommand(command, true);
    }


    private void assertExecuteCommand(String command, boolean expected) throws IOException {
        Command cmd =  JSONUtils.getLsp4jGson().fromJson(new StringReader(command), Command.class);
        LSPCommandContext context = new LSPCommandContext(cmd, getProject());
        context.setShowNotificationError(false);

        Path filePath = Files.createTempFile(null, ".ts");
        VirtualFile file = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(filePath);
        context.setFile(file);
        CommandExecutor.LSPCommandResponse result = CommandExecutor.executeCommand(context);
        Assertions.assertEquals(expected, result.exists());
    }
}

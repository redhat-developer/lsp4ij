/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * FalsePattern - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.redhat.devtools.lsp4ij.LSP4IJAssert.assertApplyWorkspaceEdit;

/**
 * Tests for {@link LSPIJUtils#applyWorkspaceEdit(WorkspaceEdit)} with Zig ZLS.
 */
public class LSPIJUtils_applyWorkspaceEdit_ZigTest extends BasePlatformTestCase {

    public void testOptimizeImports() throws IOException {
        Path filePath = Files.createTempFile(null, ".zig");
        String content = """
                const vk = @import("vulkan");
                ///standard library
                const std = @import("std");
                const glfw = @import("mach-glfw");
                const Allocator = std.mem.Allocator;
                const x = 123;
                """;
        // optimize @imports
        String json = """
                {
                  "changes": {
                    "%s": [
                      {
                        "range": {
                          "start": {
                            "line": 0,
                            "character": 0
                          },
                          "end": {
                            "line": 0,
                            "character": 0
                          }
                        },
                        "newText": "///standard library\\nconst std \\u003d @import(\\"std\\");\\nconst Allocator \\u003d std.mem.Allocator;\\n\\nconst glfw \\u003d @import(\\"mach-glfw\\");\\nconst vk \\u003d @import(\\"vulkan\\");\\n\\n"
                      },
                      {
                        "range": {
                          "start": {
                            "line": 0,
                            "character": 0
                          },
                          "end": {
                            "line": 1,
                            "character": 0
                          }
                        },
                        "newText": ""
                      },
                      {
                        "range": {
                          "start": {
                            "line": 1,
                            "character": 0
                          },
                          "end": {
                            "line": 3,
                            "character": 0
                          }
                        },
                        "newText": ""
                      },
                      {
                        "range": {
                          "start": {
                            "line": 3,
                            "character": 0
                          },
                          "end": {
                            "line": 4,
                            "character": 0
                          }
                        },
                        "newText": ""
                      },
                      {
                        "range": {
                          "start": {
                            "line": 4,
                            "character": 0
                          },
                          "end": {
                            "line": 5,
                            "character": 0
                          }
                        },
                        "newText": ""
                      }
                    ]
                  }
                }
                """.formatted(filePath.toUri().toASCIIString());
        String expected = """
                ///standard library
                const std = @import("std");
                const Allocator = std.mem.Allocator;
                
                const glfw = @import("mach-glfw");
                const vk = @import("vulkan");
                
                const x = 123;
                """;
        assertApplyWorkspaceEdit(filePath, content, json, expected,getProject());
    }

}

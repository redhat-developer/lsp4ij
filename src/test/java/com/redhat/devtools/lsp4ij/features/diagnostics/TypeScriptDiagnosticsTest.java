package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.redhat.devtools.lsp4ij.fixtures.LSPDiagnosticsFixtureTestCase;

public class TypeScriptDiagnosticsTest extends LSPDiagnosticsFixtureTestCase {

    public TypeScriptDiagnosticsTest() {
        super("*.ts");
    }

    public void testPublishDiagnostics() {
        assertDiagnostics("test.ts",
                """
                        const bar = "";
                        bar.charAt()
                        """,
                // language=json
                """
                        [
                            {
                              "range": {
                                "start": {
                                  "line": 1,
                                  "character": 4
                                },
                                "end": {
                                  "line": 1,
                                  "character": 10
                                }
                              },
                              "severity": 1,
                              "code": 2554,
                              "source": "typescript",
                              "message": "Expected 1 arguments, but got 0.",
                              "tags": [],
                              "relatedInformation": [
                                {
                                  "location": {
                                    "uri": "file:///c%3A/Users/XXX/node_modules/typescript/lib/lib.es5.d.ts",
                                    "range": {
                                      "start": {
                                        "line": 417,
                                        "character": 11
                                      },
                                      "end": {
                                        "line": 417,
                                        "character": 22
                                      }
                                    }
                                  },
                                  "message": "An argument for \\u0027pos\\u0027 was not provided."
                                }
                              ]
                            }
                          ]
                        """);
    }
}

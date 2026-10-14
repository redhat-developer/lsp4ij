/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;

/**
 * Test case for InvalidWebFilter quick fix
 */
public class WebFilterQuickFixTest extends LSPCodeActionFixtureTestCase {

    // NOTE: Using an extension other than "java" here to avoid having native Java language support skew results
    private static final String NOT_JAVA_EXTENSION = "javax";
    private static final String TEST_FILE_NAME = "InvalidWebFilter." + NOT_JAVA_EXTENSION;

    public WebFilterQuickFixTest() {
        super("*." + NOT_JAVA_EXTENSION);
    }

    public void testWebFilterQuickFix() {
        var allQuickFixes = assertCodeActions(TEST_FILE_NAME,
                IntentionActionKind.QUICK_FIX_ONLY,
                // language=JAVA
                """
package io.openliberty.sample.jakarta.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.annotation.WebFilter;

@WebFilter(<caret>)
public abstract class InvalidWebFilter implements Filter {

}""",
                        // language=JSON
                        """                
                        [
                             {
                               "title": "Add the `servletNames` attribute to @WebFilter",
                               "kind": "quickfix",
                               "diagnostics": [
                                 {
                                   "range": {
                                     "start": {
                                       "line": 5,
                                       "character": 0
                                     },
                                     "end": {
                                       "line": 5,
                                       "character": 12
                                     }
                                   },
                                   "severity": 1,
                                   "code": "CompleteWebFilterAttributes",
                                   "source": "jakarta-servlet",
                                   "message": "The annotation @WebFilter must define the attribute \\u0027urlPatterns\\u0027, \\u0027servletNames\\u0027 or \\u0027value\\u0027."
                                 }
                               ],
                               "data": {
                                 "participantId": "io.openliberty.tools.intellij.lsp4jakarta.lsp4ij.servlet.CompleteFilterAnnotationQuickFix",
                                 "documentUri": "unused",
                                 "range": {
                                   "start": {
                                     "line": 5,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 5,
                                     "character": 12
                                   }
                                 },
                                 "extendedData": {
                                   "annotation": "jakarta.servlet.annotation.WebFilter",
                                   "attribute": "servletNames",
                                   "diagnosticCode": "CompleteWebFilterAttributes"
                                 },
                                 "resourceOperationSupported": true,
                                 "commandConfigurationUpdateSupported": false
                               }
                             },
                             {
                               "title": "Add the `urlPatterns` attribute to @WebFilter",
                               "kind": "quickfix",
                               "diagnostics": [
                                 {
                                   "range": {
                                     "start": {
                                       "line": 5,
                                       "character": 0
                                     },
                                     "end": {
                                       "line": 5,
                                       "character": 12
                                     }
                                   },
                                   "severity": 1,
                                   "code": "CompleteWebFilterAttributes",
                                   "source": "jakarta-servlet",
                                   "message": "The annotation @WebFilter must define the attribute \\u0027urlPatterns\\u0027, \\u0027servletNames\\u0027 or \\u0027value\\u0027."
                                 }
                               ],
                               "data": {
                                 "participantId": "io.openliberty.tools.intellij.lsp4jakarta.lsp4ij.servlet.CompleteFilterAnnotationQuickFix",
                                 "documentUri": "unused",
                                 "range": {
                                   "start": {
                                     "line": 5,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 5,
                                     "character": 12
                                   }
                                 },
                                 "extendedData": {
                                   "annotation": "jakarta.servlet.annotation.WebFilter",
                                   "attribute": "urlPatterns",
                                   "diagnosticCode": "CompleteWebFilterAttributes"
                                 },
                                 "resourceOperationSupported": true,
                                 "commandConfigurationUpdateSupported": false
                               }
                             },
                             {
                               "title": "Add the `value` attribute to @WebFilter",
                               "kind": "quickfix",
                               "diagnostics": [
                                 {
                                   "range": {
                                     "start": {
                                       "line": 5,
                                       "character": 0
                                     },
                                     "end": {
                                       "line": 5,
                                       "character": 12
                                     }
                                   },
                                   "severity": 1,
                                   "code": "CompleteWebFilterAttributes",
                                   "source": "jakarta-servlet",
                                   "message": "The annotation @WebFilter must define the attribute \\u0027urlPatterns\\u0027, \\u0027servletNames\\u0027 or \\u0027value\\u0027."
                                 }
                               ],
                               "data": {
                                 "participantId": "io.openliberty.tools.intellij.lsp4jakarta.lsp4ij.servlet.CompleteFilterAnnotationQuickFix",
                                 "documentUri": "unused",
                                 "range": {
                                   "start": {
                                     "line": 5,
                                     "character": 0
                                   },
                                   "end": {
                                     "line": 5,
                                     "character": 12
                                   }
                                 },
                                 "extendedData": {
                                   "annotation": "jakarta.servlet.annotation.WebFilter",
                                   "attribute": "value",
                                   "diagnosticCode": "CompleteWebFilterAttributes"
                                 },
                                 "resourceOperationSupported": true,
                                 "commandConfigurationUpdateSupported": false
                               }
                             }
                           ]"""
                ,
                "Add the `servletNames` attribute to @WebFilter",
                "Add the `urlPatterns` attribute to @WebFilter",
                "Add the `value` attribute to @WebFilter");

        // Get 'Add the `servletNames` attribute to @WebFilter' quick fix
        var addServletNameQuickFix = assertFindIntentionByText("Add the `servletNames` attribute to @WebFilter", allQuickFixes);

        // Apply 'Add the `servletNames` attribute to @WebFilter' quick fix
        assertApplyCodeAction(
                // language=JAVA
                """
package io.openliberty.sample.jakarta.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.annotation.WebFilter;

@WebFilter(servletNames="")
public abstract class InvalidWebFilter implements Filter {

}""",
                // language=JSON
                """                
                        {
                          "title": "Add the `servletNames` attribute to @WebFilter",
                          "kind": "quickfix",
                          "diagnostics": [
                            {
                              "range": {
                                "start": {
                                  "line": 5,
                                  "character": 0
                                },
                                "end": {
                                  "line": 5,
                                  "character": 12
                                }
                              },
                              "severity": 1,
                              "code": "CompleteWebFilterAttributes",
                              "source": "jakarta-servlet",
                              "message": "The annotation @WebFilter must define the attribute \\u0027urlPatterns\\u0027, \\u0027servletNames\\u0027 or \\u0027value\\u0027."
                            }
                          ],
                          "edit": {
                            "changes": {},
                            "documentChanges": [
                              {
                                "textDocument": {
                                  "version": 0,
                                  "uri": "%s"
                                },
                                "edits": [
                                  {
                                    "range": {
                                      "start": {
                                        "line": 0,
                                        "character": 0
                                      },
                                      "end": {
                                        "line": 11,
                                        "character": 0
                                      }
                                    },
                                    "newText": "package io.openliberty.sample.jakarta.servlet;\\n\\nimport jakarta.servlet.Filter;\\nimport jakarta.servlet.annotation.WebFilter;\\n\\n@WebFilter(servletNames\\u003d\\"\\")\\npublic abstract class InvalidWebFilter implements Filter {\\n\\n}"
                                  }
                                ]
                              }
                            ]
                          },
                          "data": {
                            "participantId": "io.openliberty.tools.intellij.lsp4jakarta.lsp4ij.servlet.CompleteFilterAnnotationQuickFix",
                            "documentUri": "unused",
                            "range": {
                              "start": {
                                "line": 5,
                                "character": 0
                              },
                              "end": {
                                "line": 5,
                                "character": 12
                              }
                            },
                            "extendedData": {
                              "annotation": "jakarta.servlet.annotation.WebFilter",
                              "attribute": "servletNames",
                              "diagnosticCode": "CompleteWebFilterAttributes"
                            },
                            "resourceOperationSupported": true,
                            "commandConfigurationUpdateSupported": false
                          }
                        }
                        """,
                addServletNameQuickFix);
    }

}
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
public class CodeActionCancellationTest extends LSPCodeActionFixtureTestCase {

    private static final String NOT_JAVA_EXTENSION = "javax";
    private static final String TEST_FILE_NAME = "InvalidWebFilter." + NOT_JAVA_EXTENSION;

    public CodeActionCancellationTest() {
        super("*." + NOT_JAVA_EXTENSION);
    }

    public void testWebFilterQuickFix() {

        assertCodeActions(TEST_FILE_NAME,
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
            ]
            """,
            true, // simulateCancellation = true
            "Add the `servletNames` attribute to @WebFilter",
            "Add the `urlPatterns` attribute to @WebFilter",
            "Add the `value` attribute to @WebFilter"
        );
    }
}
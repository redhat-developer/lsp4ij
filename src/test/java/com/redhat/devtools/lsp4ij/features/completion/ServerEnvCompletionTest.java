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
package com.redhat.devtools.lsp4ij.features.completion;

import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;

/**
 * Completion tests by emulating LSP 'textDocument/completion' responses
 * from the liberty-ls language server
 * which returns completion items without text edit.
 */
public class ServerEnvCompletionTest extends LSPCompletionFixtureTestCase {

    public ServerEnvCompletionTest() {
        super("server.env");
    }

    // ------------ Completion on property key

    public void testCaseInsensitive() {
        // 1. Test completion items response
        assertCompletion("server.env",
                "w<caret>", """                
                        [
                           {
                             "label": "WLP_LOGGING_JSON_FIELD_MAPPINGS",
                             "kind": 10,
                             "documentation": {
                               "kind": "markdown",
                               "value": "When logs are in JSON format, use this setting to replace default field names with new field names or to omit fields from the logs. For more information, see Configurable JSON field names."
                             }
                           },
                           {
                             "label": "WLP_OUTPUT_DIR",
                             "kind": 10,
                             "documentation": {
                               "kind": "markdown",
                               "value": "The directory that contains output files for defined servers. This directory must have both read and write permissions for the user or users who start servers. By default, the server output logs and work area are stored at the `%WLP_USER_DIR%/servers/serverName` location alongside configuration and applications. If this variable is set, the output logs and work area are stored at the `%WLP_OUTPUT_DIR%/serverName location`."
                             }
                           }
                          ]"""
                ,
                "WLP_LOGGING_JSON_FIELD_MAPPINGS",
                "WLP_OUTPUT_DIR");
        // 2. Test new editor content after applying the first completion item
        assertApplyCompletionItem(0, "WLP_LOGGING_JSON_FIELD_MAPPINGS<caret>");
    }

}

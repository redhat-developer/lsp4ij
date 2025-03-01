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

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFileViewProviderFixtureTestCase;

import java.util.Map;

/**
 * Tests the semantic tokens-based file view provider for CSS, a plain text/abstract file type without semantic tokens support.
 */
public class CssSemanticTokensFileViewProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String TEST_FILE_NAME = "test.css";

    // language=css
    private static final String TEST_FILE_BODY = """
            /* Comment */
            div {
                content: "Test";
            }
            """;

    public CssSemanticTokensFileViewProviderTest() {
        super("*.css", "css");
    }

    public void testEnabled() {
        assertViewProviderEnabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                // The CSS language server doesn't support semantic tokens
                null,
                null,
                // Should only be one file-level token/element of unknown type; check the start/middle/end
                Map.ofEntries(
                        Map.entry(fileBody -> 0, isUnknown),
                        Map.entry(fileBody -> TEST_FILE_BODY.length() / 2, isUnknown),
                        Map.entry(fileBody -> TEST_FILE_BODY.length() - 1, isUnknown)
                )
        );
    }

    public void testDisabled() {
        assertViewProviderDisabled(
                TEST_FILE_NAME,
                TEST_FILE_BODY,
                null,
                null
        );
    }
}

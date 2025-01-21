/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.extractors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Tests with {@link NetworkAddressExtractor#extract(String)}
 */
public class NetworkAddressExtractorTest {

    @Test
    public void nullInput() {
        assertExtract("Debug server listening at",
                null,
                false,
                null,
                null);
    }

    @Test
    public void emptyInput() {
        assertExtract("Debug server listening at",
                "",
                false,
                null,
                null);
    }

    @Test
    public void vscode_debug_js() {
        assertExtract("Debug server listening at",
                "node /home/path/to/dap/js-debug-dap-v1.96.0/js-debug/src/dapDebugServer.js 56425 127.0.0.1",
                false,
                null,
                null);

        assertExtract("Debug server listening at",
                "Debug server listening at 127.0.0.1:56425",
                true,
                null,
                null);
    }

    @Test
    public void vscode_debug_js_pattern() {
        assertExtract("Debug server listening at ${address}:${port}",
                "node /home/path/to/dap/js-debug-dap-v1.96.0/js-debug/src/dapDebugServer.js 56425 127.0.0.1",
                false,
                null,
                null);

        assertExtract("Debug server listening at ${address}:${port}",
                "Debug server listening at 127.0.0.1:56425",
                true,
                "127.0.0.1",
                "56425");
    }

    @Test
    public void go_delve() {
        assertExtract("DAP server listening at: ${address}:${port}",
                "dlv dap",
                false,
                null,
                null);

        assertExtract("DAP server listening at: ${address}:${port}",
                "DAP server listening at: 127.0.0.1:61537",
                true,
                "127.0.0.1",
                "61537");
    }

    private static void assertExtract(@NotNull String pattern,
                              @Nullable String input,
                              boolean expectedMatches,
                              @Nullable String expectedAddress,
                              @Nullable String expectedPort) {
        NetworkAddressExtractor extractor = new NetworkAddressExtractor(pattern);
        ExtractorResult result = extractor.extract(input);
        assertEquals(expectedMatches, result.matches());
        assertEquals(expectedAddress, result.address());
        assertEquals(expectedPort, result.port());
    }
}

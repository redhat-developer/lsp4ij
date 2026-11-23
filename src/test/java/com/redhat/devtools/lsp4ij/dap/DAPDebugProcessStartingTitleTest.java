/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DAPDebugProcess#getStartingServerTaskTitle(String, String, String, Integer, DebugMode)} tests: the title of
 * the task which starts the DAP server, which the console prints too, names the file of the run configuration only when
 * it has one.
 */
class DAPDebugProcessStartingTitleTest {

    @Test
    void withAFile_namesItAndTheServer() {
        assertEquals("Attaching 'inventory.c' with 'DAP server' at 127.0.0.1:39079",
                DAPDebugProcess.getStartingServerTaskTitle("/opt/demo/inventory.c", null, "127.0.0.1", 39079, DebugMode.ATTACH));
        assertEquals("Launching 'test.ts' with 'VSCode JS Debug' at 9999",
                DAPDebugProcess.getStartingServerTaskTitle("C:\\demo\\test.ts", "VSCode JS Debug", null, 9999, DebugMode.LAUNCH));
    }

    @Test
    void withoutAFile_namesOnlyTheServer() {
        assertEquals("Attaching 'DAP server' at 127.0.0.1:39079",
                DAPDebugProcess.getStartingServerTaskTitle(null, null, "127.0.0.1", 39079, DebugMode.ATTACH));
    }

    @Test
    void withAnEmptyOrBlankFile_namesOnlyTheServer() {
        // the file of a run configuration whose file field is empty
        assertEquals("Attaching 'DAP server' at 127.0.0.1:39079",
                DAPDebugProcess.getStartingServerTaskTitle("", null, "127.0.0.1", 39079, DebugMode.ATTACH));
        assertEquals("Launching 'VSCode JS Debug'",
                DAPDebugProcess.getStartingServerTaskTitle("  ", "VSCode JS Debug", null, null, DebugMode.LAUNCH));
    }
}

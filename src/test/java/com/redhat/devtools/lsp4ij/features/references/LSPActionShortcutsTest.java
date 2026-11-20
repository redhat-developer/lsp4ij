/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * An action of LSP4IJ takes no shortcut of an action of IntelliJ in the default keymap: `LSP Reference(s)` had
 * `Ctrl+Alt+H`, the shortcut of Call Hierarchy (#801).
 */
public class LSPActionShortcutsTest extends BasePlatformTestCase {

    /**
     * The actions whose shortcut another action has too, which stay as they are until it is decided which shortcut
     * they get: `Ctrl+Alt+Z` of Go to Declaration is also Rollback Lines, and `Ctrl+Alt+E` of Go to Type Definition is
     * also Toggle Find in Selection, in the Find bar.
     */
    private static final Set<String> KNOWN_CONFLICTS = Set.of("LSP.GotoDeclaration", "LSP.GotoTypeDefinition");

    public void testAnActionOfLSP4IJTakesNoShortcutOfAnotherActionInTheDefaultKeymap() {
        var keymap = KeymapManager.getInstance().getKeymap(KeymapManager.DEFAULT_IDEA_KEYMAP);
        assertNotNull(keymap);
        var lsp4ijActions = Set.of(ActionManagerEx.getInstanceEx()
                .getPluginActions(PluginId.getId("com.redhat.devtools.lsp4ij")));
        assertTrue("LSP4IJ's actions: " + lsp4ijActions, lsp4ijActions.contains("LSP.GotoReference"));
        List<String> conflicts = new ArrayList<>();
        for (String action : lsp4ijActions) {
            if (KNOWN_CONFLICTS.contains(action)) {
                continue;
            }
            for (var shortcut : keymap.getShortcuts(action)) {
                if (shortcut instanceof KeyboardShortcut keyboardShortcut) {
                    var others = Arrays.stream(keymap.getActionIds(keyboardShortcut))
                            .filter(other -> !lsp4ijActions.contains(other))
                            .toList();
                    if (!others.isEmpty()) {
                        conflicts.add(action + " (" + keyboardShortcut + "): " + others);
                    }
                }
            }
        }
        assertEquals(List.of(), conflicts);
    }
}

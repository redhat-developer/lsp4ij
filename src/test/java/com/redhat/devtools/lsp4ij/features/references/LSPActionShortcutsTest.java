/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.references;

import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * An action of LSP4IJ takes no shortcut of a navigation action of IntelliJ in the default keymap, which LSP4IJ
 * supports for language servers too: `LSP Reference(s)` had `Ctrl+Alt+H`, the shortcut of Call Hierarchy (#801).
 * Actions of other areas, which use the same keys in views of their own, as the Database view or the VCS, are left
 * out: they depend on the product and its plugins.
 */
public class LSPActionShortcutsTest extends BasePlatformTestCase {

    private static final List<String> NAVIGATION_ACTIONS = List.of(
            "CallHierarchy", "TypeHierarchy", "MethodHierarchy",
            "GotoDeclaration", "GotoTypeDeclaration", "GotoImplementation", "GotoSuperMethod",
            "FindUsages", "ShowUsages", "HighlightUsagesInFile");

    public void testAnActionOfLSP4IJTakesNoShortcutOfANavigationActionInTheDefaultKeymap() {
        var keymap = KeymapManager.getInstance().getKeymap(KeymapManager.DEFAULT_IDEA_KEYMAP);
        assertNotNull(keymap);
        var lsp4ijActions = Set.of(ActionManagerEx.getInstanceEx()
                .getPluginActions(PluginId.getId("com.redhat.devtools.lsp4ij")));
        assertTrue("LSP4IJ's actions: " + lsp4ijActions, lsp4ijActions.contains("LSP.GotoReference"));
        assertNotEmpty(Arrays.asList(keymap.getShortcuts("CallHierarchy")));
        List<String> conflicts = new ArrayList<>();
        for (String action : lsp4ijActions) {
            for (var shortcut : keymap.getShortcuts(action)) {
                for (String navigation : NAVIGATION_ACTIONS) {
                    if (Arrays.asList(keymap.getShortcuts(navigation)).contains(shortcut)) {
                        conflicts.add(action + " (" + shortcut + "): " + navigation);
                    }
                }
            }
        }
        assertEquals(List.of(), conflicts);
    }
}

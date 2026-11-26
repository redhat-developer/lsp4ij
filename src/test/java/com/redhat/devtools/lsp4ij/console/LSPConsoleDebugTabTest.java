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
package com.redhat.devtools.lsp4ij.console;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.console.explorer.LanguageServerTreeNode;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the Debug tab of a language server in the LSP console (#2267): it shows the debug port and Suspend for the
 * language server of an extension, whose plugin can start it with them, and not for a user-defined language server,
 * which its own command starts.
 */
public class LSPConsoleDebugTabTest extends BasePlatformTestCase {

    private static final int BOUND_SECONDS = 60;

    private LanguageServerDefinition definition;
    private LSPConsoleToolWindowPanel panel;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (panel != null) {
                Disposer.dispose(panel);
            }
            if (definition != null) {
                LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
            }
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            panel = null;
            definition = null;
            super.tearDown();
        }
    }

    public void testTheDebugTabOfALanguageServerOfAnExtensionShowsTheDebugPortAndSuspend() {
        JComponent debugTab = debugTabOf(new MockLanguageServerDefinition("console-debug-tab-" + UUID.randomUUID(), false));

        assertEquals(1, UIUtil.findComponentsOfType(debugTab, PortField.class).size());
        assertEquals(1, suspendCheckBoxes(debugTab).size());
    }

    public void testTheDebugTabOfAUserDefinedLanguageServerDoesNotShowTheDebugPortAndSuspend() {
        JComponent debugTab = debugTabOf(new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null,
                "Test server", null, null, "node server.js --stdio", null, Map.of(), false, null, false, null, null,
                null, null, null, null));

        assertEmpty(UIUtil.findComponentsOfType(debugTab, PortField.class));
        assertEmpty(suspendCheckBoxes(debugTab));
    }

    /**
     * Adds the given language server, opens the LSP console, selects the node of the language server, as a click on it
     * does, and returns the Debug tab of the page that the console shows for it.
     */
    private @NotNull JComponent debugTabOf(@NotNull LanguageServerDefinition definition) {
        this.definition = definition;
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        panel = new LSPConsoleToolWindowPanel(getProject());
        var explorer = panel.getExplorer();
        PlatformTestUtil.waitWithEventsDispatching("The LSP console must show the language server",
                () -> explorer.findNodeForServer(definition) != null, BOUND_SECONDS);
        LanguageServerTreeNode node = explorer.findNodeForServer(definition);
        panel.selectDetail(node);
        PlatformTestUtil.waitWithEventsDispatching("The LSP console must show the page of the language server",
                () -> panel.hasConsolePanel(node) && mainTabs() != null, BOUND_SECONDS);

        JTabbedPane tabs = mainTabs();
        int index = tabs.indexOfTab(LanguageServerBundle.message("language.server.tab.debug"));
        assertTrue("The page has the Debug tab", index >= 0);
        return (JComponent) tabs.getComponentAt(index);
    }

    /**
     * Returns the tabs of the page of the language server, the only one which the console shows, or null when the
     * console doesn't show it yet.
     */
    private JTabbedPane mainTabs() {
        return UIUtil.findComponentsOfType(panel, JBTabbedPane.class).stream()
                .filter(tabs -> SwingUtilities.getAncestorOfClass(JTabbedPane.class, tabs) == null)
                .findFirst()
                .orElse(null);
    }

    private static @NotNull List<JBCheckBox> suspendCheckBoxes(@NotNull JComponent debugTab) {
        String text = LanguageServerBundle.message("language.server.debug.suspend");
        return UIUtil.findComponentsOfType(debugTab, JBCheckBox.class).stream()
                .filter(checkBox -> text.equals(checkBox.getText()))
                .toList();
    }
}

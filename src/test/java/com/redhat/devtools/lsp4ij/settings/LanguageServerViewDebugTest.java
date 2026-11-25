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
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the debug port and Suspend of the Debug tab of a language server (#2267), on the settings page and in the LSP
 * console, which both show a {@link LanguageServerView}. The page of the language server of an extension shows them,
 * saves them and resets them, as its plugin passes them to the language server with {@link JavaProcessCommandBuilder}.
 * The page of a user-defined language server, which its own command starts, doesn't show them, and a debug port stored
 * for it doesn't make the page modified.
 */
public class LanguageServerViewDebugTest extends BasePlatformTestCase {

    private static final String JDWP = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1044";

    private final List<LanguageServerView> views = new ArrayList<>();
    private final List<LanguageServerDefinition> definitions = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            views.forEach(Disposer::dispose);
            for (LanguageServerDefinition definition : definitions) {
                LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
                // The light project, and its settings, outlive the test
                ProjectLanguageServerSettings.getInstance(getProject()).getState().myState.remove(definition.getId());
                GlobalLanguageServerSettings.getInstance().getState().myState.remove(definition.getId());
            }
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            views.clear();
            definitions.clear();
            super.tearDown();
        }
    }

    public void testTheDebugTabOfALanguageServerOfAnExtensionShowsTheDebugPortAndSuspend() {
        var definition = addDefinition(extensionServer());

        // As the settings page and the LSP console create it
        for (boolean canExecuteInstaller : List.of(false, true)) {
            JComponent debugTab = debugTab(createView(definition, canExecuteInstaller));

            assertEquals(1, UIUtil.findComponentsOfType(debugTab, PortField.class).size());
            assertEquals(1, suspendCheckBoxes(debugTab).size());
        }
    }

    public void testTheDebugTabOfAUserDefinedLanguageServerDoesNotShowTheDebugPortAndSuspend() {
        var definition = addDefinition(userDefinedServer());

        for (boolean canExecuteInstaller : List.of(false, true)) {
            JComponent debugTab = debugTab(createView(definition, canExecuteInstaller));

            assertEmpty(UIUtil.findComponentsOfType(debugTab, PortField.class));
            assertEmpty(suspendCheckBoxes(debugTab));
        }
    }

    public void testTheDebugPortOfALanguageServerOfAnExtensionIsSavedAndReset() throws ConfigurationException {
        var definition = addDefinition(extensionServer());
        var view = createView(definition, false);
        assertFalse(view.isModified());

        view.setDebugPort("1044");
        view.setDebugSuspend(true);
        assertTrue(view.isModified());
        view.apply();

        assertFalse(view.isModified());
        var stored = storedSettings(definition);
        assertEquals("1044", stored.getDebugPort());
        assertTrue(stored.isDebugSuspend());
        // The plugin of the language server starts it with the debug port and Suspend
        assertContainsElements(new JavaProcessCommandBuilder(getProject(), definition.getId()).setJar("server.jar").create(), JDWP);

        view.setDebugPort("2000");
        view.setDebugSuspend(false);
        assertTrue(view.isModified());
        view.reset();

        assertEquals("1044", view.getDebugPort());
        assertTrue(view.isDebugSuspend());
        assertFalse(view.isModified());

        // A page opened later shows them too
        var otherView = createView(definition, true);
        assertEquals("1044", otherView.getDebugPort());
        assertTrue(otherView.isDebugSuspend());
        assertFalse(otherView.isModified());

        // An empty debug port is saved, and starts the language server without the debug agent
        view.setDebugPort("");
        view.setDebugSuspend(false);
        view.apply();

        assertFalse(view.isModified());
        assertTrue(StringUtils.isEmpty(storedSettings(definition).getDebugPort()));
        assertFalse(storedSettings(definition).isDebugSuspend());
        assertDoesntContain(new JavaProcessCommandBuilder(getProject(), definition.getId()).setJar("server.jar").create(), JDWP);
    }

    public void testADebugPortStoredForAUserDefinedLanguageServerDoesNotModifyItsPage() throws ConfigurationException {
        var definition = addDefinition(userDefinedServer());
        // As the Debug tab used to save it, when it showed the debug port and Suspend of a user-defined language server
        var settings = new ProjectLanguageServerSettings.LanguageServerDefinitionSettings();
        settings.setDebugPort("1044");
        settings.setDebugSuspend(true);
        ProjectLanguageServerSettings.getInstance(getProject()).updateSettings(definition.getId(), settings);

        var view = createView(definition, false);

        assertFalse("a debug port which the page doesn't show doesn't modify it", view.isModified());
        assertEquals("the page doesn't load what it doesn't show", "", view.getDebugPort());
        assertFalse(view.isDebugSuspend());

        // Saving what the page shows keeps what it doesn't show
        view.setServerTrace(ServerTrace.messages);
        assertTrue(view.isModified());
        view.apply();

        assertFalse(view.isModified());
        var stored = storedSettings(definition);
        assertEquals(ServerTrace.messages, stored.getServerTrace());
        assertEquals("1044", stored.getDebugPort());
        assertTrue(stored.isDebugSuspend());
    }

    private @NotNull LanguageServerDefinition addDefinition(@NotNull LanguageServerDefinition definition) {
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        definitions.add(definition);
        return definition;
    }

    /**
     * Creates the page of the given language server, as the settings page creates it, or as the LSP console does,
     * which can run its installer, and fills it from the settings.
     */
    private @NotNull LanguageServerView createView(@NotNull LanguageServerDefinition definition, boolean canExecuteInstaller) {
        var view = new LanguageServerView(definition, null, canExecuteInstaller, getProject());
        views.add(view);
        view.reset();
        return view;
    }

    private @NotNull LanguageServerSettings.LanguageServerDefinitionSettings storedSettings(@NotNull LanguageServerDefinition definition) {
        var settings = ProjectLanguageServerSettings.getInstance(getProject()).getLanguageServerSettings(definition.getId());
        assertNotNull("the settings of " + definition.getId() + " are saved", settings);
        return settings;
    }

    /**
     * Returns a language server which a plugin adds, as with the extension point, with an id of its own.
     */
    private static @NotNull LanguageServerDefinition extensionServer() {
        return new MockLanguageServerDefinition("debug-tab-test-" + UUID.randomUUID(), false);
    }

    private static @NotNull UserDefinedLanguageServerDefinition userDefinedServer() {
        return new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null, "Test server", null,
                null, "node server.js --stdio", null, Map.of(), false, null, false, null, null, null, null,
                null, null);
    }

    private static @NotNull JComponent debugTab(@NotNull LanguageServerView view) {
        JTabbedPane tabs = UIUtil.findComponentsOfType(view.getComponent(), JBTabbedPane.class).stream()
                .filter(pane -> SwingUtilities.getAncestorOfClass(JTabbedPane.class, pane) == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("The page has no tabs"));
        int index = tabs.indexOfTab(LanguageServerBundle.message("language.server.tab.debug"));
        assertTrue("The page has the Debug tab", index >= 0);
        JComponent debugTab = (JComponent) tabs.getComponentAt(index);
        assertEquals("The Debug tab shows Error reporting and Trace", 2,
                UIUtil.findComponentsOfType(debugTab, ComboBox.class).size());
        return debugTab;
    }

    private static @NotNull List<JBCheckBox> suspendCheckBoxes(@NotNull JComponent debugTab) {
        String text = LanguageServerBundle.message("language.server.debug.suspend");
        return UIUtil.findComponentsOfType(debugTab, JBCheckBox.class).stream()
                .filter(checkBox -> text.equals(checkBox.getText()))
                .toList();
    }
}

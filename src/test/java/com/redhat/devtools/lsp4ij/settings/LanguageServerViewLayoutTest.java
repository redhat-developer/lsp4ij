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

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The page of a language server, in the LSP console and in the settings (issues #374 and #324): its header is a label
 * in the font of the IDE, which shortens the name of the language server when the page is narrow and shows all of it
 * in its tooltip, and the tabs of fields scroll when they don't fit, while a tab with a JSON editor, which scrolls
 * itself, is not put in a scroll pane (#655).
 */
public class LanguageServerViewLayoutTest extends BasePlatformTestCase {

    private static final String LONG_NAME =
            "A language server with a name that is much longer than the LSP console is wide when it is narrow";

    private LanguageServerDefinition definition;
    private LanguageServerView view;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (view != null) {
                Disposer.dispose(view);
            }
            if (definition != null) {
                LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
            }
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testTheHeaderIsALabelWithTheNameOfTheLanguageServerAndItsTooltip() {
        JComponent page = createView(new NamedLanguageServerDefinition(LONG_NAME));

        JLabel header = header(page);
        assertEquals(LONG_NAME, header.getToolTipText());
        for (JComponent component : UIUtil.findComponentsOfType(page, JComponent.class)) {
            assertFalse("The header is no longer a titled border: " + component,
                    component.getBorder() instanceof TitledBorder);
        }
    }

    public void testTheHeaderShortensTheNameWhenThePageIsNarrow() {
        JComponent page = createView(new NamedLanguageServerDefinition(LONG_NAME));
        JLabel header = header(page);
        int nameWidth = header.getPreferredSize().width;
        assertTrue("The name doesn't keep the page from being narrower than it: " + page.getMinimumSize().width + " < " + nameWidth,
                page.getMinimumSize().width < nameWidth);

        int narrowWidth = JBUIScale.scale(200);
        page.setSize(narrowWidth, JBUIScale.scale(400));
        layOut(page);

        assertTrue("The header is as wide as the page, not as its name: " + header.getWidth(),
                header.getWidth() > 0 && header.getWidth() <= narrowWidth && header.getWidth() < nameWidth);
        String shownText = shownText(header);
        assertTrue("The name is shortened with an ellipsis: " + shownText,
                shownText.endsWith("...") && shownText.length() < LONG_NAME.length());
    }

    public void testTheTabsOfFieldsScroll() {
        JComponent page = createView(new NamedLanguageServerDefinition(LONG_NAME));

        JTabbedPane tabs = mainTabs(page);
        assertInstanceOf(tab(tabs, "language.server.tab.server"), JScrollPane.class);
        assertInstanceOf(tab(tabs, "language.server.tab.debug"), JScrollPane.class);
    }

    public void testTheJsonEditorsAreNotInAScrollPane() {
        JComponent page = createView(new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null,
                "Test server", null, null, "node server.js", null, Map.of(), false, null, false, null, null, null,
                null, null, null));

        JTabbedPane tabs = mainTabs(page);
        for (String tabKey : List.of("language.server.tab.configuration", "language.server.tab.installer")) {
            Component tab = tab(tabs, tabKey);
            List<EditorTextField> editors = UIUtil.findComponentsOfType((JComponent) tab, EditorTextField.class);
            assertFalse("The " + tabKey + " tab has a JSON editor", editors.isEmpty());
            for (EditorTextField editor : editors) {
                assertNull("A JSON editor of the " + tabKey + " tab is in a scroll pane",
                        SwingUtilities.getAncestorOfClass(JScrollPane.class, editor));
            }
        }
        for (String tabKey : List.of("language.server.tab.server", "language.server.tab.debug")) {
            Component tab = tab(tabs, tabKey);
            assertInstanceOf(tab, JScrollPane.class);
            assertEmpty("The " + tabKey + " tab, which scrolls, has no JSON editor",
                    UIUtil.findComponentsOfType((JComponent) tab, EditorTextField.class));
        }
    }

    private @NotNull JComponent createView(@NotNull LanguageServerDefinition definition) {
        this.definition = definition;
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        view = new LanguageServerView(definition, null, false, getProject());
        view.reset();
        return view.getComponent();
    }

    private static @NotNull JLabel header(@NotNull JComponent page) {
        List<JLabel> headers = UIUtil.findComponentsOfType(page, JLabel.class).stream()
                .filter(label -> LONG_NAME.equals(label.getText()))
                .toList();
        assertEquals("The page has one label with the name of the language server: " + headers, 1, headers.size());
        return headers.get(0);
    }

    private static @NotNull JTabbedPane mainTabs(@NotNull JComponent page) {
        return UIUtil.findComponentsOfType(page, JBTabbedPane.class).stream()
                .filter(tabs -> SwingUtilities.getAncestorOfClass(JTabbedPane.class, tabs) == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("The page has no tabs"));
    }

    private static @NotNull Component tab(@NotNull JTabbedPane tabs, @NotNull String titleKey) {
        int index = tabs.indexOfTab(LanguageServerBundle.message(titleKey));
        assertTrue("The page has the tab " + titleKey, index >= 0);
        return tabs.getComponentAt(index);
    }

    /**
     * Lays out the given component and all its descendants at their current sizes, as a window would.
     */
    private static void layOut(@NotNull Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOut(child);
            }
        }
    }

    /**
     * Returns the text a label shows at its current size, as Swing lays it out to paint it.
     */
    private static @NotNull String shownText(@NotNull JLabel label) {
        Insets insets = label.getInsets();
        var viewBounds = new Rectangle(insets.left, insets.top,
                label.getWidth() - insets.left - insets.right, label.getHeight() - insets.top - insets.bottom);
        return SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()), label.getText(), null,
                label.getVerticalAlignment(), label.getHorizontalAlignment(),
                label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
                viewBounds, new Rectangle(), new Rectangle(), label.getIconTextGap());
    }

    /**
     * A language server which is not user-defined, so its page has a header, with the given name.
     */
    private static final class NamedLanguageServerDefinition extends MockLanguageServerDefinition {

        private final String name;

        private NamedLanguageServerDefinition(@NotNull String name) {
            super("layout-test-server", false);
            this.name = name;
        }

        @Override
        public @NotNull String getDisplayName() {
            return name;
        }
    }
}

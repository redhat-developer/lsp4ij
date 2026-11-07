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

import com.intellij.application.options.editor.EditorSmartKeysConfigurableEP;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

import javax.swing.JCheckBox;
import javax.swing.JComponent;

/**
 * Linked editing is off unless it is turned on in Settings | Editor | General | Smart Keys, where LSP4IJ's options
 * are shown beside those of other languages.
 */
public class LSPSmartKeysConfigurableTest extends BasePlatformTestCase {

    private static final ExtensionPointName<EditorSmartKeysConfigurableEP> SMART_KEYS =
            ExtensionPointName.create("com.intellij.editorSmartKeysConfigurable");

    public void testTheOptionsAreShownInSmartKeys() {
        assertTrue(SMART_KEYS.getExtensionList().stream()
                .anyMatch(ep -> LSPSmartKeysConfigurable.class.getName().equals(ep.instanceClass)));
    }

    public void testTheCheckboxTurnsLinkedEditingOnAndOff() throws ConfigurationException {
        var options = LSPEditorOptions.getInstance();
        assertFalse("linked editing is off by default", options.isLinkedEditing());
        var configurable = new LSPSmartKeysConfigurable();
        try {
            JComponent component = configurable.createComponent();
            assertNotNull(component);
            // as the settings dialog does, which shows the options as they are
            configurable.reset();
            JCheckBox checkBox = UIUtil.findComponentOfType(component, JCheckBox.class);
            assertNotNull(checkBox);
            assertFalse(checkBox.isSelected());

            checkBox.setSelected(true);
            assertTrue(configurable.isModified());
            configurable.apply();
            assertTrue(options.isLinkedEditing());

            checkBox.setSelected(false);
            configurable.apply();
            assertFalse(options.isLinkedEditing());
        } finally {
            configurable.disposeUIResources();
            options.loadState(new LSPEditorOptions.Options());
        }
    }
}

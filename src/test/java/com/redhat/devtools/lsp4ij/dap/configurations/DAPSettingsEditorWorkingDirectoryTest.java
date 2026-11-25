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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.RunManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;

/**
 * Tests the working directory field of the DAP run configuration editor: empty, it shows where the Debug Adapter
 * runs, in the directory of the configuration's file, or without a file in the project's directory. Nothing new is
 * stored.
 */
public class DAPSettingsEditorWorkingDirectoryTest extends BasePlatformTestCase {

    private DAPSettingsEditor editor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        editor = new DAPSettingsEditor(getProject());
        Disposer.register(getTestRootDisposable(), editor);
    }

    public void testAnEmptyWorkingDirectoryWithAFileShowsTheFilesDirectory() throws Exception {
        var configuration = create();
        configuration.setFile("/project/sub/test.js");

        editor.resetFrom(configuration);

        assertEquals("", workingDirectory().getText());
        assertEquals(DAPBundle.message("dap.settings.editor.configuration.cwd.emptyText.file"), emptyText());
        editor.applyTo(configuration);
        assertTrue("the placeholder isn't stored: " + configuration.getWorkingDirectory(),
                StringUtils.isBlank(configuration.getWorkingDirectory()));
    }

    public void testAnEmptyWorkingDirectoryWithoutAFileShowsTheProjectDirectory() throws Exception {
        var configuration = create();

        editor.resetFrom(configuration);

        assertEquals("", workingDirectory().getText());
        assertEquals(DAPBundle.message("dap.settings.editor.configuration.cwd.emptyText"), emptyText());
        editor.applyTo(configuration);
        assertTrue("the placeholder isn't stored: " + configuration.getWorkingDirectory(),
                StringUtils.isBlank(configuration.getWorkingDirectory()));
    }

    public void testThePlaceholderFollowsTheFileAsTheUserEditsIt() {
        editor.resetFrom(create());

        // as the user chooses a file, then clears it
        file().setText("/project/sub/test.js");
        assertEquals(DAPBundle.message("dap.settings.editor.configuration.cwd.emptyText.file"), emptyText());
        file().setText("");
        assertEquals(DAPBundle.message("dap.settings.editor.configuration.cwd.emptyText"), emptyText());
    }

    public void testAStoredWorkingDirectoryIsShownAndKept() throws Exception {
        var configuration = create();
        configuration.setFile("/project/sub/test.js");
        configuration.setWorkingDirectory("/project");

        editor.resetFrom(configuration);

        assertEquals("/project", workingDirectory().getText());
        editor.applyTo(configuration);
        assertEquals("/project", configuration.getWorkingDirectory());
    }

    /**
     * Creates a DAP run configuration from the template, as one created from a file or added by the user.
     */
    private @NotNull DAPRunConfiguration create() {
        var factory = DAPConfigurationType.getInstance().getConfigurationFactories()[0];
        return (DAPRunConfiguration) RunManager.getInstance(getProject()).createConfiguration("test", factory).getConfiguration();
    }

    private @NotNull String emptyText() {
        return ((JBTextField) workingDirectory().getTextField()).getEmptyText().getText();
    }

    private @NotNull TextFieldWithBrowseButton workingDirectory() {
        return field("dap.settings.editor.configuration.cwd.field");
    }

    private @NotNull TextFieldWithBrowseButton file() {
        return field("dap.settings.editor.configuration.file.field");
    }

    /**
     * Returns the field of the editor with the label of the given key.
     */
    private @NotNull TextFieldWithBrowseButton field(@NotNull String labelKey) {
        String label = DAPBundle.message(labelKey);
        return UIUtil.findComponentsOfType(editor.getComponent(), JLabel.class).stream()
                .filter(candidate -> label.equals(candidate.getText()))
                .map(JLabel::getLabelFor)
                .filter(TextFieldWithBrowseButton.class::isInstance)
                .map(TextFieldWithBrowseButton.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field labeled " + label));
    }
}

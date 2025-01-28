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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.dap.settings.ui.DebugAdapterDescriptorFactoryPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Debug Adapter Protocol (DAP) settings editor.
 */
public class DAPSettingsEditor extends SettingsEditor<DAPRunConfiguration> {

    private final JPanel myPanel;
    private final @NotNull Project project;
    private final DebugAdapterDescriptorFactoryPanel dapPanel;

    public DAPSettingsEditor(@NotNull Project project) {
        this.project = project;
        FormBuilder builder = FormBuilder
                .createFormBuilder();
        dapPanel = new DebugAdapterDescriptorFactoryPanel(builder,
                null,
                DebugAdapterDescriptorFactoryPanel.EditionMode.EDIT_USER_DEFINED,
                true,
                project);
        myPanel = new JPanel(new BorderLayout());
        myPanel.add(builder.getPanel(), BorderLayout.CENTER);
    }

    @Override
    protected void resetEditorFrom(DAPRunConfiguration runConfiguration) {
        // Sever settings
        dapPanel.setServerName(runConfiguration.getServerName());
        dapPanel.setCommandLine(runConfiguration.getCommand());
        dapPanel.updateConnectingStrategy(runConfiguration.getConnectingServerStrategy(),
                runConfiguration.getWaitForTimeout(),
                runConfiguration.getWaitForTrace());
        dapPanel.setServerTrace(runConfiguration.getServerTrace());

        // Mappings settings
        dapPanel.setMappings(runConfiguration.getServerMappings());

        // Configuration settings
        dapPanel.setWorkingDirectory(runConfiguration.getWorkingDirectory());
        dapPanel.setFile(runConfiguration.getFile());
        dapPanel.setDebuggingType(runConfiguration.getDebuggingType());
        dapPanel.setLaunchConfiguration(runConfiguration.getLaunchParameters());
        dapPanel.setAttachConfiguration(runConfiguration.getAttachParameters());

        // Update server if at and to update tabs if needed
        dapPanel.setServerId(runConfiguration.getServerId());
    }

    @Override
    protected void applyEditorTo(@NotNull DAPRunConfiguration runConfiguration) {
        // Sever settings
        runConfiguration.setServerName(dapPanel.getServerName());
        runConfiguration.setCommand(dapPanel.getCommandLine());
        runConfiguration.setConnectingServerStrategy(dapPanel.getConnectingServerConfigurationPanel().getConnectingServerStrategy());
        runConfiguration.setWaitForTimeout(getInt(dapPanel.getConnectingServerConfigurationPanel().getTimeout()));
        runConfiguration.setWaitForTrace(dapPanel.getConnectingServerConfigurationPanel().getTrace());
        runConfiguration.setServerTrace(dapPanel.getServerTrace());

        // Mappings settings
        runConfiguration.setServerMappings(dapPanel.getMappings());

        // Configuration settings
        runConfiguration.setWorkingDirectory(dapPanel.getWorkingDirectory());
        runConfiguration.setFile(dapPanel.getFile());
        runConfiguration.setDebuggingType(dapPanel.getDebuggingType());
        runConfiguration.setLaunchParameters(dapPanel.getLaunchConfiguration());
        runConfiguration.setAttachParameters(dapPanel.getAttachConfiguration());
    }

    private static int getInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myPanel;
    }

    public @NotNull Project getProject() {
        return project;
    }

}
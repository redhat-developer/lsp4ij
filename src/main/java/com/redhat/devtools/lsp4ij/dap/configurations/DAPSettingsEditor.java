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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.settings.DebugAdapterServerConfigurable;
import com.redhat.devtools.lsp4ij.dap.settings.ui.DebugAdapterServerPanel;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Debug Adapter Protocol (DAP) settings editor.
 */
public class DAPSettingsEditor extends SettingsEditor<DAPRunConfiguration> {

    private final JPanel myPanel;
    private final @NotNull Project project;
    private final DebugAdapterServerPanel dapPanel;

    private final DebugAdapterServerListener listener = new DebugAdapterServerListener() {

        @Override
        public void handleAdded(@NotNull DebugAdapterServerListener.AddedEvent event) {
        }

        @Override
        public void handleRemoved(@NotNull DebugAdapterServerListener.RemovedEvent event) {
        }

        @Override
        public void handleChanged(@NotNull DebugAdapterServerListener.ChangedEvent event) {
            if (event.commandChanged) {
                // The command of DAP server has changed.
                var serverDefinition = event.serverDefinition;
                if (serverDefinition == dapPanel.getCurrentServer() && serverDefinition instanceof CommandLineUpdater updater) {
                    // The command modification applies to the DAP server currently being edited by the DAP settings editor.
                    // -> the command field in the UI must be updated accordingly
                    ApplicationManager.getApplication()
                            .invokeLater(() -> dapPanel.setCommandLine(updater.getCommandLine()));
                }
            }
        }
    };

    public DAPSettingsEditor(@NotNull Project project) {
        this.project = project;
        FormBuilder builder = FormBuilder
                .createFormBuilder();
        dapPanel = new DebugAdapterServerPanel(builder,
                null,
                DebugAdapterServerPanel.EditionMode.EDIT_USER_DEFINED,
                true,
                project);
        myPanel = new JPanel(new BorderLayout());
        myPanel.add(builder.getPanel(), BorderLayout.CENTER);
        DebugAdapterManager.getInstance().addDebugAdapterServerListener(listener);
    }

    @Override
    protected void resetEditorFrom(DAPRunConfiguration runConfiguration) {
        dapPanel.setServerId(runConfiguration.getServerId());

        // Sever settings
        dapPanel.setServerName(runConfiguration.getServerName());
        dapPanel.setCommandLine(runConfiguration.getCommand());
        dapPanel.setServerTrace(runConfiguration.getServerTrace());

        // Mappings settings
        dapPanel.setMappings(runConfiguration.getServerMappings());

        // Configuration settings
        dapPanel.setWorkingDirectory(runConfiguration.getWorkingDirectory());
        dapPanel.setFile(runConfiguration.getFile());
        dapPanel.setAttachAddress(runConfiguration.getAttachAddress());
        dapPanel.setAttachPort(runConfiguration.getAttachPort());
        dapPanel.setDebugMode(runConfiguration.getDebugMode());
        dapPanel.setLaunchConfigurationId(runConfiguration.getLaunchConfigurationId());
        dapPanel.setAttachConfigurationId(runConfiguration.getAttachConfigurationId());
        dapPanel.setLaunchConfiguration(runConfiguration.getLaunchConfiguration());
        dapPanel.setAttachConfiguration(runConfiguration.getAttachConfiguration());
        dapPanel.updateDebugServerWaitStrategy(runConfiguration.getDebugServerWaitStrategy(),
                runConfiguration.getConnectTimeout(),
                runConfiguration.getDebugServerReadyPattern());

        // Installer settings
        dapPanel.setInstallerConfiguration(runConfiguration.getInstallerConfiguration());

        // Update server id at the end to update
        // - selected tab if needed
        dapPanel.updateSelectedTab(runConfiguration.getServerId());
    }

    @Override
    protected void applyEditorTo(@NotNull DAPRunConfiguration runConfiguration) {
        // Sever settings
        runConfiguration.setServerId(dapPanel.getServerId());
        runConfiguration.setServerName(dapPanel.getServerName());
        runConfiguration.setCommand(dapPanel.getCommandLine());
        runConfiguration.setDebugServerWaitStrategy(dapPanel.getDebugServerWaitStrategyPanel().getDebugServerWaitStrategy());
        runConfiguration.setConnectTimeout(dapPanel.getDebugServerWaitStrategyPanel().getConnectTimeout());
        runConfiguration.setDebugServerReadyPattern(dapPanel.getDebugServerWaitStrategyPanel().getTrace());
        runConfiguration.setAttachAddress(dapPanel.getAttachAddress());
        runConfiguration.setAttachPort(dapPanel.getAttachPort());
        runConfiguration.setServerTrace(dapPanel.getServerTrace());

        // Mappings settings
        runConfiguration.setServerMappings(dapPanel.getMappings());

        // Configuration settings
        runConfiguration.setWorkingDirectory(dapPanel.getWorkingDirectory());
        runConfiguration.setFile(dapPanel.getFile());
        runConfiguration.setDebugMode(dapPanel.getDebugMode());
        runConfiguration.setLaunchConfigurationId(dapPanel.getLaunchConfigurationId());
        runConfiguration.setLaunchConfiguration(dapPanel.getLaunchConfiguration());
        runConfiguration.setAttachConfigurationId(dapPanel.getAttachConfigurationId());
        runConfiguration.setAttachConfiguration(dapPanel.getAttachConfiguration());

        // Installer settings
        runConfiguration.setInstallerConfiguration(dapPanel.getInstallerConfiguration());
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return myPanel;
    }

    public @NotNull Project getProject() {
        return project;
    }

    @Override
    protected void disposeEditor() {
        super.disposeEditor();
        dapPanel.dispose();
        DebugAdapterManager.getInstance().removeDebugAdapterServerListener(listener);
    }
}
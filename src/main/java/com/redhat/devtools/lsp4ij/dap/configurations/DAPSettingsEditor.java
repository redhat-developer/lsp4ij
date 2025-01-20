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

import com.google.common.collect.Streams;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactoryRegistry;
import com.redhat.devtools.lsp4ij.dap.descriptors.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.ui.JsonTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Debug Adapter Protocol (DAP) settings editor.
 */
public class DAPSettingsEditor extends SettingsEditor<DAPRunConfiguration> {

    private final JPanel myPanel;
    private final @NotNull Project project;

    private final JBTabbedPane mainTabbedPane;

    // Program settings
    private TextFieldWithBrowseButton workingDirectoryField;
    private TextFieldWithBrowseButton fileField;

    // DAP server settings
    private ComboBox<DebugAdapterDescriptorFactory> serverFactoryCombo;
    private JTextField serverNameField;
    private TextFieldWithBrowseButton commandField;
    private DAPConnectingServerConfigurationPanel connectingServerConfigurationPanel;
    private ComboBox<ServerTrace> serverTraceComboBox;

    private JBTabbedPane parametersTabbedPane;
    private JRadioButton launchRadioButton;
    private JRadioButton attachRadioButton;
    private JsonTextField launchParametersField;
    private JsonTextField attachParametersField;

    // DAP file mappings
    private DAPServerMappingsPanel mappingsPanel;

    private DAPTemplate currentTemplate;
    private DebugAdapterDescriptorFactory currentServerFactory;
    private boolean formLoaded;

    public DAPSettingsEditor(@NotNull Project project) {
        this.project = project;
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        mainTabbedPane = new JBTabbedPane();
        builder.addComponentFillVertically(mainTabbedPane, 0);

        // Configuration tab
        addConfigurationTab(mainTabbedPane);
        // Mappings tab
        addMappingsTab(mainTabbedPane, builder);
        // Server tab
        addServerTab(mainTabbedPane, builder);

        myPanel = new JPanel(new BorderLayout());
        myPanel.add(builder.getPanel(), BorderLayout.CENTER);
    }

    private void addConfigurationTab(JBTabbedPane tabbedPane) {
        FormBuilder configurationTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.configuration.tab"));

        workingDirectoryField = new TextFieldWithBrowseButton();
        workingDirectoryField.addBrowseFolderListener(null, null, getProject(), FileChooserDescriptorFactory.createSingleFolderDescriptor());
        configurationTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.cwd.field"), workingDirectoryField);

        fileField = new TextFieldWithBrowseButton();
        fileField.addBrowseFolderListener(null, null, getProject(), FileChooserDescriptorFactory.createSingleFileDescriptor());
        configurationTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.file.field"), fileField);

        ButtonGroup buttonGroup = new ButtonGroup();
        launchRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.configuration.debugging.launch.type"));
        buttonGroup.add(launchRadioButton);
        attachRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.configuration.debugging.attach.type"));
        buttonGroup.add(attachRadioButton);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(launchRadioButton);
        radioPanel.add(attachRadioButton);
        configurationTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.debugging.type.field"), radioPanel);

        parametersTabbedPane = new JBTabbedPane();
        launchRadioButton.addActionListener(event -> {
            selectLaunchDebuggingType();
        });
        attachRadioButton.addActionListener(event -> {
            selectAttachDebuggingType();
        });

        // Launch / Attach DAP parameters
        configurationTab.addComponentFillVertically(parametersTabbedPane, 0);

        FormBuilder launchTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.launch.tab"));
        launchParametersField = new JsonTextField(project);
        launchTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), launchParametersField);
        FormBuilder attachTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.attach.tab"));
        attachParametersField = new JsonTextField(project);
        attachTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), attachParametersField);

    }

    private void addMappingsTab(JBTabbedPane tabbedPane, FormBuilder builder) {
        FormBuilder mappingsTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.mappings.tab"));
        this.mappingsPanel = new DAPServerMappingsPanel(mappingsTab, true);
    }

    private void addServerTab(@NotNull JBTabbedPane tabbedPane,
                              @NotNull FormBuilder builder) {
        FormBuilder serverTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.server.tab"));

        serverFactoryCombo = new ComboBox<>(new DefaultComboBoxModel<>(getDapServerFactories()));
        serverFactoryCombo.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList list,
                                  @Nullable DebugAdapterDescriptorFactory serverFactory,
                                  int index,
                                  boolean selected,
                                  boolean hasFocus) {
                if (serverFactory == null) {
                    setText("");
                } else {
                    setText(serverFactory.getName());
                }
            }
        });
        serverFactoryCombo.addItemListener(e -> {
            currentServerFactory = (DebugAdapterDescriptorFactory) e.getItem();
            if (currentServerFactory instanceof UserDefinedDebugAdapterDescriptorFactory dapFactory) {
                if (dapFactory != UserDefinedDebugAdapterDescriptorFactory.NONE) {
                    loadFromDapFactory(dapFactory);
                }
            }
        });

        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.factory.field"), serverFactoryCombo);

        serverNameField = new JTextField();
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.name.field"), serverNameField);

        commandField = new TextFieldWithBrowseButton();
        commandField.addBrowseFolderListener(null, null, getProject(),
                FileChooserDescriptorFactory.createSingleFileDescriptor());
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.command.field"), commandField);

        // Connecting server configuration
        connectingServerConfigurationPanel = new DAPConnectingServerConfigurationPanel();
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.connecting.strategy.label"), connectingServerConfigurationPanel, true);

        serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.serverTrace.field"), serverTraceComboBox);
    }

    private static DebugAdapterDescriptorFactory[] getDapServerFactories() {
        List<DebugAdapterDescriptorFactory> templates = new ArrayList<>();
        templates.add(DebugAdapterDescriptorFactory.NONE);
        templates.addAll(DebugAdapterDescriptorFactoryRegistry.getInstance().getFactories());
        return templates.toArray(new DebugAdapterDescriptorFactory[0]);
    }

    private void selectAttachDebuggingType() {
        attachRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(1);
    }

    private void selectLaunchDebuggingType() {
        launchRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(0);
    }

    private void loadFromDapFactory(@NotNull UserDefinedDebugAdapterDescriptorFactory dapFactory) {
        // Update name
        serverNameField.setText(dapFactory.getName() != null ? dapFactory.getName() : "");

        // Update wait for trace
        connectingServerConfigurationPanel.update(null,
                getInt(dapFactory.getWaitForTimeout()),
                dapFactory.getWaitForTrace());

        // Update command
        String command = getCommandLine(dapFactory);
        commandField.setText(command);

        // Update mappings
        mappingsPanel.refreshMappings(dapFactory);

        // Update DAP parameters
        launchParametersField.setText(dapFactory.getLaunchConfiguration() != null ? dapFactory.getLaunchConfiguration() : "");
        launchParametersField.setCaretPosition(0);
        attachParametersField.setText(dapFactory.getAttachConfiguration() != null ? dapFactory.getAttachConfiguration() : "");
        attachParametersField.setCaretPosition(0);
    }

    private static String getCommandLine(UserDefinedDebugAdapterDescriptorFactory entry) {
        StringBuilder command = new StringBuilder();
        if (entry.getCommandLine() != null) {
            if (!command.isEmpty()) {
                command.append(' ');
            }
            command.append(entry.getCommandLine());
        }
        return command.toString();
    }


    @Override
    protected void resetEditorFrom(DAPRunConfiguration runConfiguration) {
        // Configuration settings
        workingDirectoryField.setText(runConfiguration.getWorkingDirectory());
        fileField.setText(runConfiguration.getFile());
        boolean launchType = runConfiguration.getDebuggingType() == DebuggingType.LAUNCH;
        if (launchType) {
            selectLaunchDebuggingType();
        } else {
            selectAttachDebuggingType();
        }
        launchParametersField.setText(runConfiguration.getLaunchParameters());
        attachParametersField.setText(runConfiguration.getAttachParameters());

        // Mappings settings
        List<ServerMappingSettings> languageMappings = runConfiguration.getServerMappings()
                .stream()
                .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                .collect(Collectors.toList());
        mappingsPanel.setLanguageMappings(languageMappings);

        List<ServerMappingSettings> fileTypeMappings = runConfiguration.getServerMappings()
                .stream()
                .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                .collect(Collectors.toList());
        mappingsPanel.setFileTypeMappings(fileTypeMappings);

        List<ServerMappingSettings> fileNamePatternMappings = runConfiguration.getServerMappings()
                .stream()
                .filter(mapping -> mapping.getFileNamePatterns() != null)
                .collect(Collectors.toList());
        mappingsPanel.setFileNamePatternMappings(fileNamePatternMappings);

        // Sever settings
        String serverId = runConfiguration.getServerId();
        if (StringUtils.isNotBlank(serverId)) {
            DebugAdapterDescriptorFactory factory = DebugAdapterDescriptorFactoryRegistry.getInstance().getFactoryById(serverId);
            if (factory != null) {
                currentServerFactory = factory;
                serverFactoryCombo.setSelectedItem(factory);
            }
        }
        serverNameField.setText(runConfiguration.getServerName());
        commandField.setText(runConfiguration.getCommand());
        connectingServerConfigurationPanel.update(runConfiguration.getConnectingServerStrategy(),
                runConfiguration.getWaitForTimeout(),
                runConfiguration.getWaitForTrace());
        serverTraceComboBox.setSelectedItem(runConfiguration.getServerTrace());

        // If DAP server is not configured, select 'Server' tab
        if (!formLoaded && StringUtils.isEmpty(serverId) && commandField.getText().isEmpty()) {
            mainTabbedPane.setSelectedIndex(2);
        }
        formLoaded = true;
    }

    @Override
    protected void applyEditorTo(@NotNull DAPRunConfiguration runConfiguration) {
        // Configuration settings
        runConfiguration.setWorkingDirectory(workingDirectoryField.getText());
        runConfiguration.setFile(fileField.getText());
        runConfiguration.setDebuggingType(attachRadioButton.isSelected() ? DebuggingType.ATTACH : DebuggingType.LAUNCH);
        runConfiguration.setLaunchParameters(launchParametersField.getText());
        runConfiguration.setAttachParameters(attachParametersField.getText());

        // Mappings settings
        runConfiguration.setServerMappings(mappingsPanel.getAllMappings());

        // Sever settings
        runConfiguration.setServerName(serverNameField.getText());
        runConfiguration.setCommand(commandField.getText());
        runConfiguration.setConnectingServerStrategy(connectingServerConfigurationPanel.getConnectingServerStrategy());
        runConfiguration.setWaitForTimeout(getInt(connectingServerConfigurationPanel.getTimeout()));
        runConfiguration.setWaitForTrace(connectingServerConfigurationPanel.getTrace());
        runConfiguration.setServerTrace((ServerTrace) serverTraceComboBox.getSelectedItem());

        if (currentServerFactory != null) {
            runConfiguration.setServerId(currentServerFactory.getId());
            currentServerFactory.setServerTrace((ServerTrace) serverTraceComboBox.getSelectedItem());
            if (currentServerFactory instanceof UserDefinedDebugAdapterDescriptorFactory dapFactory) {
                runConfiguration.setServerId(dapFactory.getId());
                if (dapFactory != UserDefinedDebugAdapterDescriptorFactory.NONE) {
                    dapFactory.setName(serverNameField.getText());
                    dapFactory.setCommandLine(commandField.getText());
                    dapFactory.setWaitForTimeout(connectingServerConfigurationPanel.getTimeout());
                    dapFactory.setWaitForTrace(connectingServerConfigurationPanel.getTrace());
                    dapFactory.setLanguageMappings(mappingsPanel.getLanguageMappings());
                    dapFactory.setFileTypeMappings(Streams.concat(
                            mappingsPanel.getFileTypeMappings().stream(),
                            mappingsPanel.getFileNamePatternMappings().stream()
                            ).toList());
                }
            }
        }
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

    private static FormBuilder addTab(JBTabbedPane tabbedPane, String tabTitle) {
        return addTab(tabbedPane, tabTitle, true);
    }

    @NotNull
    private static FormBuilder addTab(JBTabbedPane tabbedPane, String tabTitle, boolean addToTop) {
        FormBuilder builder = FormBuilder.createFormBuilder();
        var tabPanel = new BorderLayoutPanel();
        if (addToTop) {
            tabPanel.addToTop(builder.getPanel());
        } else {
            tabPanel.addToCenter(builder.getPanel());
        }
        tabbedPane.add(tabTitle, tabPanel);
        return builder;
    }

    public @NotNull Project getProject() {
        return project;
    }

}
/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.ConnectingServerStrategy;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPServerMappingsPanel;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterManager;
import com.redhat.devtools.lsp4ij.dap.descriptors.userdefined.UserDefinedDebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.ui.CommandLineWidget;
import com.redhat.devtools.lsp4ij.settings.ui.SchemaBackedJsonTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * UI panel to define a Debug Adapter descriptor factory.
 */
public class DebugAdapterDescriptorFactoryPanel implements Disposable {

    private static final int COMMAND_LENGTH_MAX = 140;

    private final Project project;
    private HyperlinkLabel editJsonSchemaAction;
    private DebugAdapterDescriptorFactory currentServerFactory;
    private boolean initialized;
    private JBTabbedPane mainTabbedPane;

    public enum EditionMode {
        NEW_USER_DEFINED,
        EDIT_USER_DEFINED,
        EDIT_EXTENSION;
    }

    // Server settings
    private ComboBox<DebugAdapterDescriptorFactory> serverFactoryCombo;
    private JBTextField serverNameField;
    private EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private DAPConnectingServerConfigurationPanel connectingServerConfigurationPanel;
    private ComboBox<ServerTrace> serverTraceComboBox;

    // Mappings settings
    private DAPServerMappingsPanel mappingsPanel;

    // Configuration settings
    private TextFieldWithBrowseButton workingDirectoryField;
    private TextFieldWithBrowseButton fileField;
    private JBTabbedPane parametersTabbedPane;
    private JRadioButton launchRadioButton;
    private JRadioButton attachRadioButton;
    private SchemaBackedJsonTextField launchConfigurationField;
    private SchemaBackedJsonTextField attachConfigurationField;

    public DebugAdapterDescriptorFactoryPanel(@NotNull FormBuilder builder,
                                              @Nullable JComponent description,
                                              @NotNull EditionMode mode,
                                              @NotNull Project project) {
        this(builder, description, mode, false, project);
    }

    public DebugAdapterDescriptorFactoryPanel(@NotNull FormBuilder builder,
                                              @Nullable JComponent description,
                                              @NotNull EditionMode mode,
                                              boolean showFactoryCombo,
                                              @NotNull Project project) {
        this.project = project;
        createUI(builder, description, mode, showFactoryCombo);
    }

    private void createUI(FormBuilder builder, JComponent description, EditionMode mode, boolean showFactoryCombo) {
        mainTabbedPane = new JBTabbedPane();
        builder.addComponentFillVertically(mainTabbedPane, 0);

        // Server tab
        addServerTab(mainTabbedPane, description, mode, showFactoryCombo);
        // Mappings tab
        addMappingsTab(mainTabbedPane, mode);
        // Configuration tab
        addConfigurationTab(mainTabbedPane);
    }

    private void addServerTab(JBTabbedPane tabbedPane, JComponent description, EditionMode mode, boolean showFactoryCombo) {
        FormBuilder serverTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.server.tab"));
        if (description != null) {
            serverTab.addComponent(description, 0);
        }
        if (showFactoryCombo) {
            serverFactoryCombo = new ComboBox<>(new DefaultComboBoxModel<>(getServerFactories()));
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
            JPanel serverFactoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            serverFactoryPanel.add(serverFactoryCombo);
            serverFactoryPanel.add(new JLabel(DAPBundle.message("dap.settings.editor.server.factory.or")));

            var hyperLink = createHyperlinkLabel();
            serverFactoryPanel.add(hyperLink);
            serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.factory.field"), serverFactoryPanel);

        }
        if (mode == EditionMode.NEW_USER_DEFINED) {
            // Server name
            createServerNameField(serverTab);
        }
        if (mode != EditionMode.EDIT_EXTENSION) {
            environmentVariables = new EnvironmentVariablesComponent();
            serverTab.addComponent(environmentVariables);
            // Command line
            createCommandLineField(serverTab);
        }
        // Connecting server configuration
        connectingServerConfigurationPanel = new DAPConnectingServerConfigurationPanel();
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.connecting.strategy.label"), connectingServerConfigurationPanel, true);

        serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.serverTrace.field"), serverTraceComboBox);
    }

    private @NotNull HyperlinkLabel createHyperlinkLabel() {
        var hyperLink = new HyperlinkLabel(DAPBundle.message("dap.settings.editor.server.factory.create"));
        hyperLink.addHyperlinkListener(e -> {
            var dialog = new NewDebugAdapterDescriptorFactoryDialog(project);
            dialog.show();
            if (dialog.isOK()) {
                var createdFactory = dialog.getCreatedFactory();
                if (createdFactory != null) {
                    serverFactoryCombo.setModel(new DefaultComboBoxModel<>(getServerFactories()));
                    serverFactoryCombo.setSelectedItem(createdFactory);
                }
            }
        });
        return hyperLink;
    }

    private static DebugAdapterDescriptorFactory[] getServerFactories() {
        List<DebugAdapterDescriptorFactory> factories = new ArrayList<>();
        factories.add(DebugAdapterDescriptorFactory.NONE);
        factories.addAll(DebugAdapterManager.getInstance().getFactories());
        return factories.toArray(new DebugAdapterDescriptorFactory[0]);
    }

    private void addMappingsTab(JBTabbedPane tabbedPane, EditionMode mode) {
        FormBuilder mappingsTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.mappings.tab"));
        this.mappingsPanel = new DAPServerMappingsPanel(mappingsTab, mode != EditionMode.EDIT_EXTENSION);
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
        // Launch / Attach DAP parameters
        configurationTab.addComponentFillVertically(parametersTabbedPane, 0);

        FormBuilder launchTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.launch.tab"));
        launchConfigurationField = new SchemaBackedJsonTextField(project);
        launchTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), launchConfigurationField);
        FormBuilder attachTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.attach.tab"));
        attachConfigurationField = new SchemaBackedJsonTextField(project);
        attachTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), attachConfigurationField);

        // Select by default launch debugging type.
        selectLaunchDebuggingType();
        // Add radio listeners.
        launchRadioButton.addActionListener(event -> {
            selectLaunchDebuggingType();
        });
        attachRadioButton.addActionListener(event -> {
            selectAttachDebuggingType();
        });

    }

    private void selectAttachDebuggingType() {
        attachRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(1);
    }

    private void selectLaunchDebuggingType() {
        launchRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(0);
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

    private void createServerNameField(FormBuilder builder) {
        serverNameField = new JBTextField();
        builder.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.name.field"), serverNameField);
    }

    private void createCommandLineField(FormBuilder builder) {
        commandLine = new CommandLineWidget();
        JBScrollPane scrollPane = new JBScrollPane(commandLine);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        JLabel previewCommandLabel = createLabelForComponent("", scrollPane);
        updatePreviewCommand(commandLine, previewCommandLabel, project);
        builder.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.command.field"), scrollPane, true);
        builder.addComponent(previewCommandLabel, 0);
    }

    /**
     * Update preview command label with expanded macro response if needed.
     *
     * @param commandLine         the command line which could contains macro syntax.
     * @param previewCommandLabel the preview command label.
     * @param project             the project.
     * @see <a href="https://www.jetbrains.com/help/idea/built-in-macros.html">Built In Macro</a>
     */
    private static void updatePreviewCommand(@NotNull CommandLineWidget commandLine,
                                             @NotNull JLabel previewCommandLabel,
                                             @NotNull Project project) {
        commandLine.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLabel(previewCommandLabel);
            }

            private void updateLabel(JLabel previewCommandLabel) {
                String preview = UserDefinedLanguageServerDefinition.resolveCommandLine(commandLine.getText(), project);
                if (preview.equals(commandLine.getText())) {
                    previewCommandLabel.setToolTipText("");
                    previewCommandLabel.setText("");
                } else {
                    previewCommandLabel.setToolTipText(preview);
                    String shortPreview = preview.length() > COMMAND_LENGTH_MAX ? preview.substring(0, COMMAND_LENGTH_MAX) + "..." : preview;
                    previewCommandLabel.setText(shortPreview);
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLabel(previewCommandLabel);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLabel(previewCommandLabel);
            }
        });
    }


    private static JLabel createLabelForComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
        JLabel label = new JLabel(UIUtil.replaceMnemonicAmpersand(labelText));
        label.setLabelFor(component);
        return label;
    }

    // Server settings

    public void setServerId(String serverId) {
        if (StringUtils.isNotBlank(serverId)) {
            DebugAdapterDescriptorFactory factory = DebugAdapterManager.getInstance().getFactoryById(serverId);
            if (factory != null) {
                currentServerFactory = factory;
                if (serverFactoryCombo != null) {
                    serverFactoryCombo.setSelectedItem(factory);
                }
            }
        }
        if (!initialized) {
            // Add Listener when server is selected, it must update Configuration / Mappings and Server tabs
            if (serverFactoryCombo != null) {
                serverFactoryCombo.addItemListener(e -> {
                    currentServerFactory = (DebugAdapterDescriptorFactory) e.getItem();
                    if (currentServerFactory instanceof UserDefinedDebugAdapterDescriptorFactory dapFactory) {
                        if (dapFactory != UserDefinedDebugAdapterDescriptorFactory.NONE) {
                            loadFromDapFactory(dapFactory);
                        }
                    }
                });
            }
            // If DAP server is not configured, the Server tab must be selected
            if (StringUtils.isEmpty(serverId) && getCommandLine().isEmpty()) {
                mainTabbedPane.setSelectedIndex(0);
            } else {
                mainTabbedPane.setSelectedIndex(2);
            }
            initialized = true;
        }
    }

    private void loadFromDapFactory(@NotNull UserDefinedDebugAdapterDescriptorFactory dapFactory) {
        setServerId(dapFactory.getId());
        // Update name
        setServerName(dapFactory.getDisplayName());

        // Update wait for trace
        updateConnectingStrategy(null, getInt(dapFactory.getWaitForTimeout()),
                dapFactory.getWaitForTrace());

        // Update command
        String command = getCommandLine(dapFactory);
        this.setCommandLine(command);

        // Update mappings
        mappingsPanel.refreshMappings(dapFactory.getLanguageMappings(), dapFactory.getFileTypeMappings());

        // Update DAP parameters
        setLaunchConfiguration(dapFactory.getLaunchConfiguration());
        setAttachConfiguration(dapFactory.getAttachConfiguration());
    }

    private static int getInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
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

    // Server settings

    public JBTextField getServerNameField() {
        return serverNameField;
    }

    public String getServerName() {
        if (serverNameField == null) {
            return "";
        }
        return serverNameField.getText();
    }

    public void setServerName(String serverName) {
        if(serverNameField != null) {
            this.serverNameField.setText(getText(serverName));
        }
    }

    public void updateConnectingStrategy(ConnectingServerStrategy connectingServerStrategy, int waitForTimeout, String waitForTrace) {
        connectingServerConfigurationPanel.update(connectingServerStrategy, waitForTimeout, waitForTrace);
    }

    public EnvironmentVariablesComponent getEnvironmentVariables() {
        return environmentVariables;
    }

    private @NlsSafe @Nullable String getText(@Nullable String text) {
        return text != null ? text : "";
    }

    public String getCommandLine() {
        return commandLine.getText();
    }

    public CommandLineWidget getCommandLineWidget() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine.setText(getText(commandLine));
    }

    public DAPConnectingServerConfigurationPanel getConnectingServerConfigurationPanel() {
        return connectingServerConfigurationPanel;
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) serverTraceComboBox.getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        serverTraceComboBox.setSelectedItem(serverTrace);
    }

    // Mappings settings

    public DAPServerMappingsPanel getMappingsPanel() {
        return mappingsPanel;
    }

    public @NotNull List<ServerMappingSettings> getMappings() {
        return mappingsPanel.getAllMappings();
    }

    public void setMappings(@NotNull List<ServerMappingSettings> serverMappings) {
        List<ServerMappingSettings> languageMappings = serverMappings
                .stream()
                .filter(mapping -> !StringUtils.isEmpty(mapping.getLanguage()))
                .collect(Collectors.toList());
        mappingsPanel.setLanguageMappings(languageMappings);

        List<ServerMappingSettings> fileTypeMappings = serverMappings
                .stream()
                .filter(mapping -> !StringUtils.isEmpty(mapping.getFileType()))
                .collect(Collectors.toList());
        mappingsPanel.setFileTypeMappings(fileTypeMappings);

        List<ServerMappingSettings> fileNamePatternMappings = serverMappings
                .stream()
                .filter(mapping -> mapping.getFileNamePatterns() != null)
                .collect(Collectors.toList());
        mappingsPanel.setFileNamePatternMappings(fileNamePatternMappings);
    }

    // Configuration settings

    public String getWorkingDirectory() {
        return workingDirectoryField.getText();
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectoryField.setText(getText(workingDirectory));
    }

    public String getFile() {
        return fileField.getText();
    }

    public void setFile(String file) {
        this.fileField.setText(getText(file));
    }

    @NotNull
    public DebuggingType getDebuggingType() {
        return attachRadioButton.isSelected() ? DebuggingType.ATTACH : DebuggingType.LAUNCH;
    }

    public void setDebuggingType(@Nullable DebuggingType debuggingType) {
        boolean attachType = debuggingType != null && debuggingType == DebuggingType.ATTACH;
        if (attachType) {
            selectAttachDebuggingType();
        } else {
            selectLaunchDebuggingType();
        }
    }

    public String getLaunchConfiguration() {
        return launchConfigurationField.getText();
    }

    public void setLaunchConfiguration(String launchConfiguration) {
        this.launchConfigurationField.setText(getText(launchConfiguration));
        this.launchConfigurationField.setCaretPosition(0);
    }

    public String getAttachConfiguration() {
        return attachConfigurationField.getText();
    }

    public void setAttachConfiguration(String attachConfiguration) {
        this.attachConfigurationField.setText(getText(attachConfiguration));
        this.attachConfigurationField.setCaretPosition(0);
    }

    public Project getProject() {
        return project;
    }

    public ComboBox<ServerTrace> getServerTraceComboBox() {
        return serverTraceComboBox;
    }

    @Override
    public void dispose() {

    }
}

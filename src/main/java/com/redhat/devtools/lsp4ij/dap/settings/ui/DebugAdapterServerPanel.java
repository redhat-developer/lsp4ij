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
import com.intellij.execution.configuration.EnvironmentVariablesData;
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
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.dap.*;
import com.redhat.devtools.lsp4ij.dap.client.LaunchUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPServerMappingsPanel;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.ui.CommandLineWidget;
import com.redhat.devtools.lsp4ij.settings.ui.InstallerPanel;
import com.redhat.devtools.lsp4ij.settings.ui.SchemaBackedJsonTextField;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory.DEFAULT_ATTACH_CONFIGURATION_ARRAY;
import static com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory.DEFAULT_LAUNCH_CONFIGURATION_ARRAY;
import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

/**
 * UI panel to define a Debug Adapter Server.
 */
public class DebugAdapterServerPanel implements Disposable {

    private static final int COMMAND_LENGTH_MAX = 140;

    private final Project project;
    private DebugAdapterServerDefinition currentServer;
    private boolean initialized;
    private JBTabbedPane mainTabbedPane;
    private List<LaunchConfiguration> launchConfigurations;
    private AttachFieldAndPreviewLabel attachPortPreview;
    private AttachFieldAndPreviewLabel attachAddressPreview;
    private InstallerPanel installerPanel;
    // Server settings
    private ComboBox<DebugAdapterServerDefinition> debugAdapterServerCombo;
    private JBTextField serverNameField;
    private @Nullable String serverUrl;
    private HyperlinkLabel serverUrlHyperlink;
    // Server / Launch settings
    private @Nullable EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private DAPDebugServerWaitStrategyPanel debugServerWaitStrategyPanel;
    // Server trace
    private ComboBox<ServerTrace> serverTraceComboBox;
    // Mappings settings
    private DAPServerMappingsPanel mappingsPanel;
    // Configuration settings
    private JBTabbedPane parametersTabbedPane;
    private JRadioButton launchRadioButton;
    private JRadioButton attachRadioButton;
    // for launch
    private TextFieldWithBrowseButton workingDirectoryField;
    private TextFieldWithBrowseButton fileField;
    private ComboBox<LaunchConfiguration> launchCombo;
    private SchemaBackedJsonTextField launchConfigurationField;
    // for attach
    private JBTextField attachAddressField;
    private JBTextField attachPortField;
    private ComboBox<LaunchConfiguration> attachCombo;
    private SchemaBackedJsonTextField attachConfigurationField;
    public DebugAdapterServerPanel(@NotNull FormBuilder builder,
                                   @Nullable JComponent description,
                                   @NotNull EditionMode mode,
                                   @NotNull Project project) {
        this(builder, description, mode, false, project);
    }

    public DebugAdapterServerPanel(@NotNull FormBuilder builder,
                                   @Nullable JComponent description,
                                   @NotNull EditionMode mode,
                                   boolean showFactoryCombo,
                                   @NotNull Project project) {
        this.project = project;
        createUI(builder, description, mode, showFactoryCombo);
    }

    private static DebugAdapterServerDefinition[] getServerDefinitions() {
        List<DebugAdapterServerDefinition> servers = new ArrayList<>();
        servers.add(UserDefinedDebugAdapterServerDefinition.NONE);
        servers.addAll(DebugAdapterManager.getInstance().getDebugAdapterServers());
        return servers.toArray(new DebugAdapterServerDefinition[0]);
    }

    private static ComboBox<LaunchConfiguration> createLaunchCombo() {
        ComboBox<LaunchConfiguration> combo = new ComboBox<>();
        combo.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList list,
                                  @Nullable LaunchConfiguration launch,
                                  int index,
                                  boolean selected,
                                  boolean hasFocus) {
                if (launch == null) {
                    setText("");
                } else {
                    setText(launch.getName());
                }
            }
        });
        return combo;
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
                String preview = resolveCommandLine(commandLine.getText(), project);
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

    @NotNull
    private static String getCommandLine(@NotNull UserDefinedDebugAdapterServerDefinition entry) {
        StringBuilder command = new StringBuilder();
        if (entry.getCommandLine() != null) {
            if (!command.isEmpty()) {
                command.append(' ');
            }
            command.append(entry.getCommandLine());
        }
        return command.toString();
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
        if (mode != EditionMode.EDIT_EXTENSION) {
            // Installer tab
            addInstallerTab(mainTabbedPane);
        }
    }

    private void addServerTab(JBTabbedPane tabbedPane, JComponent description, EditionMode mode, boolean showFactoryCombo) {
        FormBuilder serverTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.server.tab"));
        if (description != null) {
            serverTab.addComponent(description, 0);
        }
        if (showFactoryCombo) {
            debugAdapterServerCombo = new ComboBox<>(new DefaultComboBoxModel<>(getServerDefinitions()));
            debugAdapterServerCombo.setRenderer(new SimpleListCellRenderer<>() {
                @Override
                public void customize(@NotNull JList list,
                                      @Nullable DebugAdapterServerDefinition serverDefinition,
                                      int index,
                                      boolean selected,
                                      boolean hasFocus) {
                    if (serverDefinition == null) {
                        setText("");
                    } else {
                        setText(serverDefinition.getName());
                    }
                }
            });
            // Add Listener when server is selected, it must update Configuration / Mappings and Server tabs
            debugAdapterServerCombo.addItemListener(e -> {
                currentServer = (DebugAdapterServerDefinition) e.getItem();
                if (currentServer instanceof UserDefinedDebugAdapterServerDefinition userDefinedServer) {
                    if (userDefinedServer != UserDefinedDebugAdapterServerDefinition.NONE) {
                        loadFromServerDefinition(userDefinedServer);
                    } else {
                        setServerUrl(null);
                    }
                }
            });

            JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            serverPanel.add(debugAdapterServerCombo);
            serverPanel.add(new JLabel(DAPBundle.message("dap.settings.editor.server.factory.or")));

            var hyperLink = createHyperlinkLabel();
            serverPanel.add(hyperLink);
            serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.factory.field"), serverPanel);

        }
        createServerUrl(serverTab);
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
        debugServerWaitStrategyPanel = new DAPDebugServerWaitStrategyPanel();
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.connecting.strategy.label"), debugServerWaitStrategyPanel, true);

        serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
        serverTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.serverTrace.field"), serverTraceComboBox);
    }

    private @NotNull HyperlinkLabel createHyperlinkLabel() {
        var hyperLink = new HyperlinkLabel(DAPBundle.message("dap.settings.editor.server.factory.create"));
        hyperLink.addHyperlinkListener(e -> {
            var dialog = new NewDebugAdapterServerDialog(project);
            dialog.show();
            if (dialog.isOK()) {
                var createdFactory = dialog.getCreatedServer();
                if (createdFactory != null) {
                    debugAdapterServerCombo.setModel(new DefaultComboBoxModel<>(getServerDefinitions()));
                    debugAdapterServerCombo.setSelectedItem(createdFactory);
                }
            }
        });
        return hyperLink;
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
        launchRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.configuration.debug.mode.launch"));
        buttonGroup.add(launchRadioButton);
        attachRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.configuration.debug.mode.attach"));
        buttonGroup.add(attachRadioButton);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(launchRadioButton);
        radioPanel.add(attachRadioButton);
        configurationTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.debug.mode"), radioPanel);

        // Launch / Attach DAP parameters
        parametersTabbedPane = new JBTabbedPane();
        // Launch tab
        createLaunchTab(parametersTabbedPane);
        // Attach tab
        createAttachTab(parametersTabbedPane);
        configurationTab.addComponentFillVertically(parametersTabbedPane, 0);

        // Select by default launch debugging type.
        selectLaunchDebugMode();
        // Add radio listeners.
        launchRadioButton.addActionListener(event -> {
            selectLaunchDebugMode();
        });
        attachRadioButton.addActionListener(event -> {
            selectAttachDebugMode();
        });

    }

    private void addInstallerTab(@NotNull JBTabbedPane tabbedPane) {
        FormBuilder installerTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.installer.tab"), false);
        this.installerPanel = new InstallerPanel(installerTab, false, project);
    }

    public void setCommandLineUpdater(@Nullable CommandLineUpdater commandLineUpdater) {
        this.installerPanel.setCommandLineUpdater(commandLineUpdater);
    }

    private void createLaunchTab(@NotNull JBTabbedPane tabbedPane) {
        FormBuilder launchTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.launch.tab"));

        launchCombo = createLaunchCombo();
        launchCombo.addItemListener(e -> {
            LaunchConfiguration selected = (LaunchConfiguration) e.getItem();
            setLaunchConfiguration(selected.getContent());
        });
        launchTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.launch.use"), launchCombo);
        launchConfigurationField = new SchemaBackedJsonTextField(project);
        launchTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), launchConfigurationField);
    }

    private void createAttachTab(@NotNull JBTabbedPane tabbedPane) {
        FormBuilder attachTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.configuration.parameters.attach.tab"));

        attachAddressField = new JBTextField();
        attachAddressPreview = new AttachFieldAndPreviewLabel(attachAddressField, field -> {
            String address = LaunchUtils.resolveAttachAddress(field.getText(),
                    LaunchUtils.getDapParameters(attachConfigurationField.getText(), Collections.emptyMap()));
            return address;
        });
        attachTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.attach.address.field"),
                attachAddressPreview);

        attachPortField = new JBTextField();
        attachPortPreview = new AttachFieldAndPreviewLabel(attachPortField, field -> {
            int port = LaunchUtils.resolveAttachPort(field.getText(),
                    LaunchUtils.getDapParameters(attachConfigurationField.getText(), Collections.emptyMap()));
            return port != -1 ? String.valueOf(port) : "?";
        });
        attachTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.configuration.attach.port.field"),
                attachPortPreview);

        attachCombo = createLaunchCombo();
        attachCombo.addItemListener(e -> {
            LaunchConfiguration selected = (LaunchConfiguration) e.getItem();
            setAttachConfiguration(selected.getContent());
        });
        attachTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.launch.use"), attachCombo);
        attachConfigurationField = new SchemaBackedJsonTextField(project);
        attachConfigurationField.getDocument()
                .addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
                    @Override
                    public void documentChanged(com.intellij.openapi.editor.event.@NotNull DocumentEvent event) {
                        attachAddressPreview.updatePreview();
                        attachPortPreview.updatePreview();
                    }
                });
        attachTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.configuration.parameters.field"), attachConfigurationField);
    }

    public void refreshLaunchConfigurations(@Nullable List<LaunchConfiguration> launchConfigurations) {
        this.launchConfigurations = launchConfigurations;
        if (launchConfigurations != null) {
            List<LaunchConfiguration> launches = launchConfigurations
                    .stream()
                    .filter(l -> l.getType() == DebugMode.LAUNCH)
                    .toList();
            launchCombo.setModel(new DefaultComboBoxModel<LaunchConfiguration>(launches.toArray(new LaunchConfiguration[0])));
            if (!launches.isEmpty()) {
                setLaunchConfiguration(launches.get(0).getContent());
            }
            List<LaunchConfiguration> attaches = launchConfigurations
                    .stream()
                    .filter(l -> l.getType() == DebugMode.ATTACH)
                    .toList();
            attachCombo.setModel(new DefaultComboBoxModel<LaunchConfiguration>(attaches.toArray(new LaunchConfiguration[0])));
            if (!attaches.isEmpty()) {
                setAttachConfiguration(attaches.get(0).getContent());
                if (launches.isEmpty()) {
                    // The DAP server can support only "attach", select "attach" debug mode.
                    selectAttachDebugMode();
                }
            }
        }
    }

    private void selectAttachDebugMode() {
        attachRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(1);
    }

    private void selectLaunchDebugMode() {
        launchRadioButton.setSelected(true);
        parametersTabbedPane.setSelectedIndex(0);
    }

    private void createServerUrl(@NotNull FormBuilder serverTab) {
        // Add hidden server url
        serverUrlHyperlink = new HyperlinkLabel();
        serverUrlHyperlink.setTextWithHyperlink(LanguageServerBundle.message("language.server.url", ""));
        serverUrlHyperlink.setVisible(false);
        serverTab.addComponent(serverUrlHyperlink, 0);
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

    // Server settings
    public void updateSelectedTab(@Nullable String serverId) {
        if (!initialized) {
            // If DAP server is not configured, the Server tab must be selected
            if (StringUtils.isEmpty(serverId) && getCommandLine().isEmpty()) {
                mainTabbedPane.setSelectedIndex(0);
            } else {
                mainTabbedPane.setSelectedIndex(2);
            }
            initialized = true;
        }
    }

    private void loadFromServerDefinition(@NotNull UserDefinedDebugAdapterServerDefinition serverDefinition) {
        // Update name
        setServerName(serverDefinition.getDisplayName());

        // Update url
        setServerUrl(serverDefinition.getUrl());

        // Update wait for trace
        updateDebugServerWaitStrategy(null, serverDefinition.getConnectTimeout(),
                serverDefinition.getDebugServerReadyPattern());

        // Update command
        String command = getCommandLine(serverDefinition);
        this.setCommandLine(command);
        setEnvData(EnvironmentVariablesData.create(serverDefinition.getUserEnvironmentVariables(), serverDefinition.isIncludeSystemEnvironmentVariables()));

        // Update mappings
        mappingsPanel.refreshMappings(serverDefinition.getLanguageMappings(), serverDefinition.getFileTypeMappings());

        // Update DAP parameters
        var launchConfigurations = serverDefinition.getLaunchConfigurations();
        refreshLaunchConfigurations(launchConfigurations);
        setAttachAddress(serverDefinition.getAttachAddress());
        setAttachPort(serverDefinition.getAttachPort());

        // Update installer configuration
        setInstallerConfiguration(serverDefinition.getInstallerConfiguration());
    }

    public JBTextField getServerNameField() {
        return serverNameField;
    }

    public String getServerId() {
        var serverDefinition = (DebugAdapterServerDefinition) debugAdapterServerCombo.getSelectedItem();
        return serverDefinition != null ? serverDefinition.getId() : "";
    }

    // Server settings

    public void setServerId(@Nullable String serverId) {
        if (StringUtils.isNotBlank(serverId)) {
            DebugAdapterServerDefinition serverDefinition = DebugAdapterManager.getInstance().getDebugAdapterServerById(serverId);
            if (serverDefinition != null) {
                currentServer = serverDefinition;
                if (debugAdapterServerCombo != null) {
                    debugAdapterServerCombo.setSelectedItem(serverDefinition);
                } else {
                    if (currentServer instanceof UserDefinedDebugAdapterServerDefinition userDefinedServer) {
                        loadFromServerDefinition(userDefinedServer);
                    }
                }
            }
        } else {
            // Initialize launch configuration with default value
            launchCombo.setModel(new DefaultComboBoxModel<>(DEFAULT_LAUNCH_CONFIGURATION_ARRAY));
            setLaunchConfiguration(DEFAULT_LAUNCH_CONFIGURATION_ARRAY[0].getContent());
            attachCombo.setModel(new DefaultComboBoxModel<>(DEFAULT_ATTACH_CONFIGURATION_ARRAY));
            setAttachConfiguration(DEFAULT_ATTACH_CONFIGURATION_ARRAY[0].getContent());
        }
    }

    public String getServerName() {
        if (serverNameField == null) {
            return "";
        }
        return serverNameField.getText();
    }

    public void setServerName(String serverName) {
        if (serverNameField != null) {
            this.serverNameField.setText(getText(serverName));
        }
    }

    public @Nullable String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(@Nullable String serverUrl) {
        this.serverUrl = serverUrl;
        if (serverUrl == null || StringUtils.isEmpty(serverUrl)) {
            serverUrlHyperlink.setVisible(false);
        } else {
            serverUrlHyperlink.setVisible(true);
            serverUrlHyperlink.setHyperlinkTarget(serverUrl);
        }
    }

    public void updateDebugServerWaitStrategy(@Nullable DebugServerWaitStrategy debugServerWaitStrategy,
                                              int waitForTimeout,
                                              @Nullable String debugServerReadyPattern) {
        debugServerWaitStrategyPanel.update(debugServerWaitStrategy, waitForTimeout, debugServerReadyPattern);
    }

    private @Nullable EnvironmentVariablesComponent getEnvironmentVariables() {
        return environmentVariables;
    }

    private @NlsSafe @Nullable String getText(@Nullable String text) {
        return text != null ? text : "";
    }

    public String getCommandLine() {
        return commandLine.getText();
    }

    public void setCommandLine(String commandLine) {
        this.commandLine.setText(getText(commandLine));
    }

    public CommandLineWidget getCommandLineWidget() {
        return commandLine;
    }

    public void setEnvData(@Nullable EnvironmentVariablesData envData) {
        if (environmentVariables != null) {
            environmentVariables.setEnvData(envData != null ? envData : EnvironmentVariablesData.DEFAULT);
        }
    }

    public @NotNull EnvironmentVariablesData getEnvData() {
        if (environmentVariables != null) {
            return environmentVariables.getEnvData();
        }
        return EnvironmentVariablesData.DEFAULT;
    }

    public DAPDebugServerWaitStrategyPanel getDebugServerWaitStrategyPanel() {
        return debugServerWaitStrategyPanel;
    }

    public ServerTrace getServerTrace() {
        return (ServerTrace) serverTraceComboBox.getSelectedItem();
    }

    public void setServerTrace(ServerTrace serverTrace) {
        serverTraceComboBox.setSelectedItem(serverTrace);
    }

    public DAPServerMappingsPanel getMappingsPanel() {
        return mappingsPanel;
    }

    // Mappings settings

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

    public String getWorkingDirectory() {
        return workingDirectoryField.getText();
    }

    // Configuration settings

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectoryField.setText(getText(workingDirectory));
    }

    public String getFile() {
        return fileField.getText();
    }

    public void setFile(String file) {
        this.fileField.setText(getText(file));
    }

    public String getAttachAddress() {
        return attachAddressField.getText();
    }

    public void setAttachAddress(String attachAddress) {
        this.attachAddressField.setText(getText(attachAddress));
    }

    public String getAttachPort() {
        return attachPortField.getText();
    }

    public void setAttachPort(String attachPort) {
        this.attachPortField.setText(getText(attachPort));
    }

    @NotNull
    public DebugMode getDebugMode() {
        return attachRadioButton.isSelected() ? DebugMode.ATTACH : DebugMode.LAUNCH;
    }

    public void setDebugMode(@Nullable DebugMode debugMode) {
        boolean attachType = debugMode != null && debugMode == DebugMode.ATTACH;
        if (attachType) {
            selectAttachDebugMode();
        } else {
            selectLaunchDebugMode();
        }
    }

    public String getLaunchConfigurationId() {
        LaunchConfiguration selected = (LaunchConfiguration) launchCombo.getSelectedItem();
        if (selected != null) {
            return selected.getId();
        }
        return "";
    }

    public void setLaunchConfigurationId(@Nullable String launchConfigurationId) {
        selectLaunchConfigurationById(launchConfigurationId, launchCombo);
    }

    public String getLaunchConfiguration() {
        return launchConfigurationField.getText();
    }

    public void setLaunchConfiguration(@Nullable String launchConfiguration) {
        this.launchConfigurationField.setText(getText(launchConfiguration));
        this.launchConfigurationField.setCaretPosition(0);
    }

    public String getAttachConfiguration() {
        return attachConfigurationField.getText();
    }

    public void setAttachConfiguration(@Nullable String attachConfiguration) {
        this.attachConfigurationField.setText(getText(attachConfiguration));
        this.attachConfigurationField.setCaretPosition(0);
    }

    public String getAttachConfigurationId() {
        LaunchConfiguration selected = (LaunchConfiguration) attachCombo.getSelectedItem();
        if (selected != null) {
            return selected.getId();
        }
        return "";
    }

    public void setAttachConfigurationId(@Nullable String attachConfigurationId) {
        selectLaunchConfigurationById(attachConfigurationId, attachCombo);
    }

    private void selectLaunchConfigurationById(@Nullable String configurationId,
                                               @NotNull ComboBox<LaunchConfiguration> combo) {
        if (configurationId == null || launchConfigurations == null || launchConfigurations.isEmpty()) {
            return;
        }
        var existingConfiguration = launchConfigurations
                .stream()
                .filter(l -> l.getId().equals(configurationId))
                .findFirst();
        if (existingConfiguration.isPresent()) {
            combo.setSelectedItem(existingConfiguration.get());
        }
    }

    public List<LaunchConfiguration> getLaunchConfigurations() {
        return launchConfigurations;
    }

    public Project getProject() {
        return project;
    }

    public ComboBox<ServerTrace> getServerTraceComboBox() {
        return serverTraceComboBox;
    }

    public @Nullable String getInstallerConfiguration() {
        return installerPanel != null ? installerPanel.getInstallerConfigurationWidget().getText() : null;
    }

    public void setInstallerConfiguration(@Nullable String installerConfiguration) {
        if (installerPanel == null) {
            return;
        }
        var installerConfigurationWidget = installerPanel.getInstallerConfigurationWidget();
        installerConfigurationWidget.setText(getText(installerConfiguration));
        installerConfigurationWidget.setCaretPosition(0);
    }

    public @Nullable DebugAdapterServerDefinition getCurrentServer() {
        return currentServer;
    }

    @Override
    public void dispose() {
        if (installerPanel != null) {
            installerPanel.dispose();
        }
    }

    public enum EditionMode {
        NEW_USER_DEFINED,
        EDIT_USER_DEFINED,
        EDIT_EXTENSION;
    }
}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.PortField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.UIConfiguration;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.LSPClientConfigurationJsonSchemaFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.BUNDLE;
import static com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils.resolveCommandLine;

/**
 * Language server panel which show information about language server in several tabs:
 *
 * <ul>
 *     <li>Server tab</li>
 *     <li>Mappings tab</li>
 *     <li>Configuration tab which hosts Server / Client configuration tabs</li>
 *     <li>Debug tab</li>
 * </ul>
 */
public class LanguageServerPanel implements Disposable {

    private static final int COMMAND_LENGTH_MAX = 140;

    private final Project project;
    private final ComboBox<ErrorReportingKind> errorReportingKindCombo = new ComboBox<>(new DefaultComboBoxModel<>(ErrorReportingKind.values()));
    private final ComboBox<ServerTrace> serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
    private final PortField debugPortField = new PortField();
    private final JBCheckBox debugSuspendCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.debug.suspend"));
    private final boolean canExecuteInstaller;
    private final WorkspaceFolderStrategy workspaceFolderStrategy;
    private final JBCheckBox expandConfigurationCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.configuration.expand"));
    private HyperlinkLabel editJsonSchemaAction;
    private JBTextField serverName;
    private @Nullable EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private TextFieldWithBrowseButton workingDir;
    private @Nullable TransportFields transportFields;
    private ServerMappingsPanel mappingsPanel;
    private @Nullable SchemaBackedJsonTextField configurationWidget;
    private String configurationSchemaContent;
    private JsonTextField initializationOptionsWidget;
    private JsonTextField experimentalWidget;
    private @Nullable JsonTextField clientConfigurationWidget;
    private @Nullable InstallerPanel installerPanel;
    private @Nullable String serverUrl;
    private HyperlinkLabel serverUrlHyperlink;
    private WorkspaceFoldersPanel workspaceFoldersPanel;

    public LanguageServerPanel(@NotNull FormBuilder builder,
                               @Nullable JComponent description,
                               @NotNull UIConfiguration uiConfiguration,
                               boolean canExecuteInstaller,
                               @Nullable Project project) {
        this(builder, description, uiConfiguration, canExecuteInstaller, null, project);
    }

    public LanguageServerPanel(@NotNull FormBuilder builder,
                               @Nullable JComponent description,
                               @NotNull UIConfiguration uiConfiguration,
                               boolean canExecuteInstaller,
                               @Nullable WorkspaceFolderStrategy workspaceFolderStrategy,
                               @Nullable Project project) {
        this.canExecuteInstaller = canExecuteInstaller;
        this.workspaceFolderStrategy = workspaceFolderStrategy;
        this.project = project;
        createUI(builder, description, uiConfiguration);
    }

    private static FormBuilder addTab(@NotNull JBTabbedPane tabbedPane,
                                      @NotNull String tabTitle) {
        return addTab(tabbedPane, tabTitle, true);
    }

    @NotNull
    private static FormBuilder addTab(@NotNull JBTabbedPane tabbedPane,
                                      @NotNull String tabTitle, boolean addToTop) {
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

    /**
     * Update preview working directory label with expanded macro response if needed.
     *
     * @param workingDir         the working directory field which could contains macro syntax.
     * @param previewLabel       the preview label.
     * @param project            the project.
     * @see <a href="https://www.jetbrains.com/help/idea/built-in-macros.html">Built In Macro</a>
     */
    private static void updatePreviewWorkingDir(@NotNull TextFieldWithBrowseButton workingDir,
                                                @NotNull JLabel previewLabel,
                                                @NotNull Project project) {
        workingDir.getTextField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateLabel(previewLabel);
            }

            private void updateLabel(JLabel previewLabel) {
                String text = workingDir.getText();
                if (text.isBlank()) {
                    previewLabel.setToolTipText("");
                    previewLabel.setText("");
                    return;
                }
                String preview = resolveCommandLine(text, project);
                if (preview.equals(text)) {
                    previewLabel.setToolTipText("");
                    previewLabel.setText("");
                } else {
                    previewLabel.setToolTipText(preview);
                    String shortPreview = preview.length() > COMMAND_LENGTH_MAX ? preview.substring(0, COMMAND_LENGTH_MAX) + "..." : preview;
                    previewLabel.setText(shortPreview);
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateLabel(previewLabel);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateLabel(previewLabel);
            }
        });
    }

    private static JLabel createLabelForComponent(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
        JLabel label = new JLabel(UIUtil.replaceMnemonicAmpersand(labelText));
        label.setLabelFor(component);
        return label;
    }

    private void createUI(@NotNull FormBuilder builder,
                          @Nullable JComponent description,
                          @NotNull UIConfiguration uiConfiguration) {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        builder.addComponentFillVertically(tabbedPane, 0);

        // Server tab
        addServerTab(tabbedPane, description, uiConfiguration);
        // Mappings tab
        addMappingsTab(tabbedPane, uiConfiguration);

        // Configuration tab to fill LSP Configuration + LSP Initialize Options
        addConfigurationTab(tabbedPane, uiConfiguration);

        // Workspace Folders tab
        addWorkspaceFoldersTab(tabbedPane, uiConfiguration);

        // Installer tab to fill installer of the LSP server
        addInstallerTab(tabbedPane, uiConfiguration);

        // Debug tab
        addDebugTab(tabbedPane, uiConfiguration);
    }

    private void addServerTab(@NotNull JBTabbedPane tabbedPane,
                              @Nullable JComponent description,
                              @NotNull UIConfiguration uiConfiguration) {
        FormBuilder serverTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.server"));
        createServerUrl(serverTab);
        if (description != null) {
            serverTab.addComponent(description, 0);
        }
        if (uiConfiguration.isShowServerName()) {
            // Server name
            createServerNameField(serverTab);
        }
        if (uiConfiguration.isShowCommandLine()) {
            environmentVariables = new EnvironmentVariablesComponent();
            serverTab.addComponent(environmentVariables);
            // Command line
            createCommandLineField(serverTab);
            // Working directory
            createWorkingDirField(serverTab);
            // Standard input and output, or a socket
            createTransportFields(serverTab);
        }
    }

    private void createServerUrl(@NotNull FormBuilder serverTab) {
        // Add hidden server url
        serverUrlHyperlink = new HyperlinkLabel();
        serverUrlHyperlink.setTextWithHyperlink(LanguageServerBundle.message("language.server.url", ""));
        serverUrlHyperlink.setVisible(false);
        serverTab.addComponent(serverUrlHyperlink, 0);
    }

    private void addMappingsTab(@NotNull JBTabbedPane tabbedPane,
                                @NotNull UIConfiguration configuration) {
        FormBuilder mappingsTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.mappings"), false);
        this.mappingsPanel = new ServerMappingsPanel(mappingsTab, configuration.isServerMappingsEditable());
    }

    private void addConfigurationTab(@NotNull JBTabbedPane tabbedPane,
                                     @NotNull UIConfiguration configuration) {

        boolean showServerConfiguration = configuration.isShowServerConfiguration();
        boolean showServerInitializationOptions = configuration.isShowServerInitializationOptions();
        boolean showServerExperimental = configuration.isShowServerExperimental();
        boolean showServerTab = showServerConfiguration || showServerInitializationOptions || showServerExperimental;
        boolean showClientConfiguration = configuration.isShowClientConfiguration();

        if (!showServerTab && !showClientConfiguration) {
            // Don't show Configuration tab
            return;
        }

        FormBuilder configurationTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.configuration"), false);

        JBTabbedPane configurationTabbedPane = new JBTabbedPane();
        configurationTab.addComponentFillVertically(configurationTabbedPane, 0);

        if (showServerTab) {
            FormBuilder serverConfigurationTab = addTab(configurationTabbedPane, LanguageServerBundle.message("language.server.tab.configuration.server"), false);
            if (showServerConfiguration) {
                createConfigurationField(serverConfigurationTab);
            }
            if (showServerInitializationOptions) {
                createInitializationOptionsTabField(serverConfigurationTab);
            }
            if (showServerExperimental) {
                createExperimentalTabField(serverConfigurationTab);
            }
        }

        if (showClientConfiguration) {
            FormBuilder clientConfigurationTab = addTab(configurationTabbedPane, LanguageServerBundle.message("language.server.tab.configuration.client"), false);
            createClientConfigurationField(clientConfigurationTab);
        }
    }

    private void addInstallerTab(@NotNull JBTabbedPane tabbedPane, @NotNull UIConfiguration uiConfiguration) {
        if (!uiConfiguration.isShowInstaller()) {
            return;
        }
        FormBuilder installerTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.installer"), false);
        this.installerPanel = new InstallerPanel(installerTab, canExecuteInstaller, project);
        Disposer.register(this, installerPanel);
    }

    public void setCommandLineUpdater(@Nullable CommandLineUpdater commandLineUpdater) {
        if (installerPanel != null) {
            this.installerPanel.setCommandLineUpdater(commandLineUpdater);
        }
    }

    public void addPreInstallAction(@NotNull Runnable action) {
        if (installerPanel != null) {
            this.installerPanel.addPreInstallAction(action);
        }
    }

    public void addPostInstallAction(@NotNull Runnable action) {
        if (installerPanel != null) {
            this.installerPanel.addPostInstallAction(action);
        }
    }

    private void addWorkspaceFoldersTab(@NotNull JBTabbedPane tabbedPane, @NotNull UIConfiguration uiConfiguration) {
        if (!uiConfiguration.isShowWorkspaceFolders()) {
            return;
        }
        workspaceFoldersPanel = new WorkspaceFoldersPanel(project, workspaceFolderStrategy);
        Disposer.register(this, workspaceFoldersPanel);
        JScrollPane scrollPane = new JBScrollPane(workspaceFoldersPanel);
        tabbedPane.addTab(LanguageServerBundle.message("language.server.tab.workspaceFolders"), scrollPane);
    }

    @Nullable
    public WorkspaceFoldersPanel getWorkspaceFoldersPanel() {
        return workspaceFoldersPanel;
    }

    private void addDebugTab(@NotNull JBTabbedPane tabbedPane,
                             @NotNull UIConfiguration configuration) {
        if (!configuration.isShowDebug()) {
            return;
        }
        FormBuilder debugTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.debug"));

        // Error reporting kind
        createErrorReportingCombo(debugTab);

        // Server trace
        debugTab.addLabeledComponent(LanguageServerBundle.message("language.server.trace"), serverTraceComboBox);

        if (configuration.isShowDebugPortAndSuspend()) {
            // Debug port + suspend
            debugTab
                    .addLabeledComponent(LanguageServerBundle.message("language.server.debug.port"), debugPortField)
                    .addComponent(debugSuspendCheckBox);
        }
    }

    private void createErrorReportingCombo(@NotNull FormBuilder builder) {

        errorReportingKindCombo.setRenderer(BuilderKt.textListCellRenderer(value -> {
            String text = LanguageServerBundle.message("language.server.error.reporting.none");
            if (value != null) {
                switch (value) {
                    case as_notification ->
                            text = LanguageServerBundle.message("language.server.error.reporting.as_notification");
                    case in_log -> text = LanguageServerBundle.message("language.server.error.reporting.in_log");
                }
            }
            return text;
        }));

        var helpLabel = ContextHelpLabel.createWithLink("",
                LanguageServerBundle.message("language.server.error.reporting.context.help.tooltip"),
                LanguageServerBundle.message("language.server.error.reporting.context.help.link"),
                () -> {
                    BrowserUtil.browse("https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#responseMessage");
                });

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(errorReportingKindCombo);
        panel.add(helpLabel);

        builder.addLabeledComponent(LanguageServerBundle.message("language.server.error.reporting"), panel, 0);
    }

    private void createServerNameField(@NotNull FormBuilder builder) {
        serverName = new JBTextField();
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.serverName"), serverName);
    }

    private void createCommandLineField(@NotNull FormBuilder builder) {
        commandLine = new CommandLineWidget();
        JBScrollPane scrollPane = new JBScrollPane(commandLine);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        JLabel previewCommandLabel = createLabelForComponent("", scrollPane);
        updatePreviewCommand(commandLine, previewCommandLabel, project);
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.command"), scrollPane, true);
        builder.addComponent(previewCommandLabel, 0);
    }

    private void createWorkingDirField(@NotNull FormBuilder builder) {
        workingDir = new TextFieldWithBrowseButton();
        workingDir.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project));
        JLabel previewWorkingDirLabel = createLabelForComponent("", workingDir);
        updatePreviewWorkingDir(workingDir, previewWorkingDirLabel, project);
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.workingDir"), workingDir);
        builder.addComponent(previewWorkingDirLabel, 0);
    }

    private void createTransportFields(@NotNull FormBuilder builder) {
        transportFields = new TransportFields();
        transportFields.addTo(builder);
        transportFields.addChangeListener(this::updateCommandFields);
        updateCommandFields();
    }

    /**
     * Enables the command line and working directory for a language server which LSP4IJ starts.
     */
    private void updateCommandFields() {
        boolean startsLanguageServer = getTransport().startsLanguageServer();
        if (commandLine != null) {
            commandLine.setEnabled(startsLanguageServer);
        }
        if (workingDir != null) {
            workingDir.setEnabled(startsLanguageServer);
        }
    }

    private void createConfigurationField(@NotNull FormBuilder builder) {
        // Create the hyperlink "Edit JSON Schema" / "Associate with JSON Schema".
        editJsonSchemaAction = new HyperlinkLabel(LanguageServerBundle.message("language.server.configuration.json.schema.associate"));
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(expandConfigurationCheckBox);
        headerPanel.add(editJsonSchemaAction);

        builder.addLabeledComponent(LanguageServerBundle.message("language.server.configuration"), headerPanel);
        editJsonSchemaAction.addHyperlinkListener(e -> {
            var dialog = new EditJsonSchemaDialog(project, configurationSchemaContent);
            dialog.show();
            if (dialog.isOK()) {
                // Associate the Server / Configuration editor with the new JSON content
                this.setConfigurationSchemaContent(dialog.getJsonSchemaContent());
            }
        });

        // Create the Server / Configuration Json editor
        configurationWidget = new SchemaBackedJsonTextField(project);
        builder.addComponentFillVertically(configurationWidget, 0);
    }

    private void createInitializationOptionsTabField(@NotNull FormBuilder builder) {
        initializationOptionsWidget = new JsonTextField(project);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("language.server.initializationOptions"), initializationOptionsWidget);
    }

    private void createExperimentalTabField(@NotNull FormBuilder builder) {
        experimentalWidget = new JsonTextField(project);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("language.server.experimental"), experimentalWidget);
    }

    private void createClientConfigurationField(@NotNull FormBuilder builder) {
        clientConfigurationWidget = new JsonTextField(project);
        clientConfigurationWidget.setJsonFilename(LSPClientConfigurationJsonSchemaFileProvider.CLIENT_SETTINGS_JSON_FILE_NAME);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("language.server.configuration"), clientConfigurationWidget);
    }

    public JBTextField getServerName() {
        return serverName;
    }

    public @Nullable Map<String, String> getUserEnvironmentVariables() {
        return environmentVariables != null ? environmentVariables.getEnvs() : null;
    }

    public boolean isIncludeSystemEnvironmentVariables() {
        return environmentVariables != null && environmentVariables.getEnvData().isPassParentEnvs();
    }

    public void setEnvData(@Nullable EnvironmentVariablesData envData) {
        if (environmentVariables != null && envData != null) {
            environmentVariables.setEnvData(envData);
        }
    }

    public @Nullable EnvironmentVariablesData getEnvData() {
        return environmentVariables != null ? environmentVariables.getEnvData() : null;
    }

    public @Nullable CommandLineWidget getCommandLineWidget() {
        return commandLine;
    }

    public @Nullable String getCommandLine() {
        return commandLine != null ? commandLine.getText() : null;
    }

    public void setCommandLine(@Nullable String commandLine) {
        if (this.commandLine == null) {
            return;
        }
        this.commandLine.setText(commandLine != null ? commandLine : "");
    }

    public @Nullable String getWorkingDir() {
        return workingDir != null ? workingDir.getText() : null;
    }

    public void setWorkingDir(@Nullable String workingDir) {
        if (this.workingDir == null) {
            return;
        }
        this.workingDir.setText(workingDir != null ? workingDir : "");
    }

    /**
     * Returns how LSP4IJ talks to the language server, as the server tab shows it.
     *
     * @return how LSP4IJ talks to the language server.
     */
    public @NotNull UserDefinedTransport getTransport() {
        return transportFields != null ? transportFields.getTransport() : UserDefinedTransport.STDIO;
    }

    /**
     * Shows how LSP4IJ talks to the language server in the server tab.
     *
     * @param transport how LSP4IJ talks to the language server.
     */
    public void setTransport(@NotNull UserDefinedTransport transport) {
        if (transportFields != null) {
            transportFields.setTransport(transport);
        }
    }

    public ServerMappingsPanel getMappingsPanel() {
        return mappingsPanel;
    }

    public @Nullable String getConfigurationContent() {
        return configurationWidget != null ? configurationWidget.getText() : null;
    }

    public void setConfigurationContent(@Nullable String configurationContent) {
        if (configurationWidget == null) {
            return;
        }
        configurationWidget.setText(configurationContent != null ? configurationContent : "");
        configurationWidget.setCaretPosition(0);
    }

    public boolean isExpandConfiguration() {
        return expandConfigurationCheckBox.isSelected();
    }

    public void setExpandConfiguration(boolean expandConfiguration) {
        expandConfigurationCheckBox.setSelected(expandConfiguration);
    }

    public @Nullable String getConfigurationSchemaContent() {
        return configurationSchemaContent;
    }

    public void setConfigurationSchemaContent(@Nullable String configurationSchemaContent) {
        this.configurationSchemaContent = configurationSchemaContent;
        if (configurationWidget == null) {
            return;
        }
        if (configurationSchemaContent != null && StringUtils.isNotBlank(configurationSchemaContent)) {
            configurationWidget.associateWithJsonSchema(configurationSchemaContent);
            editJsonSchemaAction.setHyperlinkText(LanguageServerBundle.message("language.server.configuration.json.schema.edit"));
        } else {
            configurationWidget.resetJsonSchema();
            editJsonSchemaAction.setHyperlinkText(LanguageServerBundle.message("language.server.configuration.json.schema.associate"));
        }
    }

    public @Nullable String getInitializationOptionsContent() {
        return initializationOptionsWidget != null ? initializationOptionsWidget.getText() : null;
    }

    public void setInitializationOptionsContent(@Nullable String initializationOptionsContent) {
        if (initializationOptionsWidget == null) {
            return;
        }
        initializationOptionsWidget.setText(initializationOptionsContent != null ? initializationOptionsContent : "");
        initializationOptionsWidget.setCaretPosition(0);
    }

    public @Nullable String getExperimentalContent() {
        return experimentalWidget != null ? experimentalWidget.getText() : null;
    }

    public void setExperimentalContent(@Nullable String experimentalContent) {
        if (experimentalWidget == null) {
            return;
        }
        experimentalWidget.setText(experimentalContent != null ? experimentalContent : "");
        experimentalWidget.setCaretPosition(0);
    }

    public @Nullable String getClientConfigurationContent() {
        return clientConfigurationWidget != null ? clientConfigurationWidget.getText() : null;
    }

    public void setClientConfigurationContent(@Nullable String clientConfigurationContent) {
        if (clientConfigurationWidget == null) {
            return;
        }
        clientConfigurationWidget.setText(clientConfigurationContent != null ? clientConfigurationContent : "");
        clientConfigurationWidget.setCaretPosition(0);
    }

    public @Nullable String getInstallerConfigurationContent() {
        return installerPanel != null ? installerPanel.getInstallerConfigurationWidget().getText() : null;
    }

    public void setInstallerConfigurationContent(@Nullable String installerConfigurationContent) {
        if (installerPanel == null) {
            return;
        }
        var installerConfigurationWidget = installerPanel.getInstallerConfigurationWidget();
        installerConfigurationWidget.setText(installerConfigurationContent != null ? installerConfigurationContent : "");
        installerConfigurationWidget.setCaretPosition(0);
    }

    public void setWorkspaceFolderStrategyConfiguration(@Nullable String workspaceFolderStrategyConfiguration) {
        if (workspaceFoldersPanel == null) {
            return;
        }
        workspaceFoldersPanel.setJsonConfiguration(workspaceFolderStrategyConfiguration);
    }

    /**
     * Returns an error for each JSON field whose content is not valid JSON, in tab order.
     *
     * <p>The content is parsed with the lenient Gson parser which reads it when the language server uses it,
     * so what that parser accepts (comments for instance) stays valid, and so does an empty field.</p>
     *
     * @return an error for each JSON field whose content is not valid JSON.
     */
    public @NotNull List<ValidationInfo> validateJsonFields() {
        List<ValidationInfo> errors = new ArrayList<>();
        // The language server reads these three once their variables, such as $PROJECT_DIR$, are expanded
        addJsonError("language.server.validation.json.configuration", getConfigurationContent(), true, errors);
        addJsonError("language.server.validation.json.initializationOptions",
                getInitializationOptionsContent(),
                true,
                errors);
        addJsonError("language.server.validation.json.experimental", getExperimentalContent(), true, errors);
        addJsonError("language.server.validation.json.clientConfiguration",
                getClientConfigurationContent(),
                false,
                errors);
        addJsonError("language.server.validation.json.installer", getInstallerConfigurationContent(), false, errors);
        addJsonError("language.server.validation.json.workspaceFolders",
                workspaceFoldersPanel != null ? workspaceFoldersPanel.getJsonConfiguration() : null,
                false,
                errors);
        return errors;
    }

    /**
     * Returns an error when the transport can't be used: a language server which runs already needs its port, a fixed
     * port isn't 0, and the command of a language server on a port which LSP4IJ chooses takes it as
     * {@value UserDefinedTransport#PORT_MACRO}, or the language server never learns it.
     *
     * @return the error of the transport, or null if it can be used.
     */
    public @Nullable ValidationInfo validateTransport() {
        if (transportFields == null) {
            return null;
        }
        var error = transportFields.validate();
        if (error != null) {
            return error;
        }
        var transport = transportFields.getTransport();
        if (transport.mode() != UserDefinedTransport.Mode.STDIO && transport.startsLanguageServer() && transport.port() == 0
                // an empty command has an error of its own
                && commandLine != null && !commandLine.getText().isBlank()
                && !commandLine.getText().contains(UserDefinedTransport.PORT_MACRO)) {
            return new ValidationInfo(LanguageServerBundle.message("language.server.validation.socket.port.macro",
                    UserDefinedTransport.PORT_MACRO), commandLine);
        }
        return null;
    }

    private void addJsonError(@NotNull @PropertyKey(resourceBundle = BUNDLE) String fieldKey,
                              @Nullable String content,
                              boolean expanded,
                              @NotNull List<ValidationInfo> errors) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        String reason = jsonError(content);
        if (reason == null && expanded && content.contains("$")) {
            // A value can be valid JSON only until its variables are expanded, as with a Windows path in a string
            String expandedReason = jsonError(resolveCommandLine(content, project));
            if (expandedReason != null) {
                reason = LanguageServerBundle.message("language.server.validation.json.expanded", expandedReason);
            }
        }
        if (reason != null) {
            String message = LanguageServerBundle.message("language.server.validation.json.invalid",
                    LanguageServerBundle.message(fieldKey),
                    reason);
            // No component: an error bound to a field only shows on the tab of that field,
            // and the new language server dialog opens on the Server tab
            errors.add(new ValidationInfo(message));
        }
    }

    /**
     * Returns why the content is not valid JSON, or null if it is.
     */
    private static @Nullable String jsonError(@NotNull String content) {
        try {
            JsonParser.parseString(content);
            return null;
        } catch (JsonParseException e) {
            // The reader error, wrapped by Gson, tells where the content breaks; newer Gson
            // versions add a troubleshooting link on a second line
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return Objects.toString(cause.getMessage(), "").lines().findFirst().orElse("");
        }
    }

    public JBCheckBox getDebugSuspendCheckBox() {
        return debugSuspendCheckBox;
    }

    public PortField getDebugPortField() {
        return debugPortField;
    }

    public ComboBox<ServerTrace> getServerTraceComboBox() {
        return serverTraceComboBox;
    }

    public ComboBox<ErrorReportingKind> getErrorReportingKindCombo() {
        return errorReportingKindCombo;
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

    @Override
    public void dispose() {
        if (configurationWidget != null) {
            // Only release the schema provider slot; the virtual file is
            // about to be garbage-collected, so renaming it is unnecessary
            // and would call LSPIJUtils.getFile() which requires a
            // non-blocking read action that cannot run on the EDT.
            configurationWidget.releaseJsonSchemaIndex();
        }
        // InstallerPanel and WorkspaceFoldersPanel are automatically disposed via Disposer.register
    }

}

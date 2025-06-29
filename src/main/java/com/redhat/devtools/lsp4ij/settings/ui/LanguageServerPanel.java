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

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ContextHelpLabel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.PortField;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.UIConfiguration;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.LSPClientConfigurationJsonSchemaFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Map;

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
    private final JBCheckBox useIntegerIdsCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.use.integer.ids"));
    private final boolean canExecuteInstaller;
    private final JBCheckBox expandConfigurationCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.configuration.expand"));
    private HyperlinkLabel editJsonSchemaAction;
    private JBTextField serverName;
    private @Nullable EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private ServerMappingsPanel mappingsPanel;
    private @Nullable SchemaBackedJsonTextField configurationWidget;
    private String configurationSchemaContent;
    private JsonTextField initializationOptionsWidget;
    private JsonTextField experimentalWidget;
    private @Nullable JsonTextField clientConfigurationWidget;
    private @Nullable InstallerPanel installerPanel;
    private @Nullable String serverUrl;
    private HyperlinkLabel serverUrlHyperlink;

    public LanguageServerPanel(@NotNull FormBuilder builder,
                               @Nullable JComponent description,
                               @NotNull UIConfiguration uiConfiguration,
                               boolean canExecuteInstaller,
                               @Nullable Project project) {
        this.canExecuteInstaller = canExecuteInstaller;
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
        }
        
        // Use Integer IDs
        serverTab.addComponent(useIntegerIdsCheckBox);
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
        FormBuilder mappingsTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.mappings"));
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

        errorReportingKindCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
            String text = LanguageServerBundle.message("language.server.error.reporting.none");
            if (value != null) {
                switch (value) {
                    case as_notification ->
                            text = LanguageServerBundle.message("language.server.error.reporting.as_notification");
                    case in_log -> text = LanguageServerBundle.message("language.server.error.reporting.in_log");
                }
            }
            label.setText(text);
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

    public JBCheckBox getDebugSuspendCheckBox() {
        return debugSuspendCheckBox;
    }

    public JBCheckBox getUseIntegerIdsCheckBox() {
        return useIntegerIdsCheckBox;
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
            configurationWidget.resetJsonSchema();
        }
        if (installerPanel != null) {
            installerPanel.dispose();
        }
    }

}

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
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.settings.ErrorReportingKind;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import com.redhat.devtools.lsp4ij.settings.jsonSchema.LSPClientConfigurationJsonSchemaFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

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
    private HyperlinkLabel editJsonSchemaAction;

    public enum EditionMode {
        NEW_USER_DEFINED,
        EDIT_USER_DEFINED,
        EDIT_EXTENSION;
    }

    private JBTextField serverName;
    private EnvironmentVariablesComponent environmentVariables;
    private CommandLineWidget commandLine;
    private ServerMappingsPanel mappingsPanel;

    private final ComboBox<ErrorReportingKind> errorReportingKindCombo = new ComboBox<>(new DefaultComboBoxModel<>(ErrorReportingKind.values()));
    private final ComboBox<ServerTrace> serverTraceComboBox = new ComboBox<>(new DefaultComboBoxModel<>(ServerTrace.values()));
    private final PortField debugPortField = new PortField();
    private final JBCheckBox debugSuspendCheckBox = new JBCheckBox(LanguageServerBundle.message("language.server.debug.suspend"));

    private SchemaBackedJsonTextField configurationWidget;
    private String configurationSchemaContent;
    private JsonTextField initializationOptionsWidget;
    private JsonTextField clientConfigurationWidget;

    public LanguageServerPanel(@NotNull FormBuilder builder,
                               @Nullable JComponent description,
                               @NotNull EditionMode mode,
                               @NotNull Project project) {
        this.project = project;
        createUI(builder, description, mode);
    }

    private void createUI(FormBuilder builder, JComponent description, EditionMode mode) {
        JBTabbedPane tabbedPane = new JBTabbedPane();
        builder.addComponentFillVertically(tabbedPane, 0);

        // Server tab
        addServerTab(tabbedPane, description, mode);
        // Mappings tab
        addMappingsTab(tabbedPane, mode);
        if (mode != EditionMode.EDIT_EXTENSION) {
            // Configuration tab to fill LSP Configuration + LSP Initialize Options
            addConfigurationTab(tabbedPane);
        }
        // Debug tab
        addDebugTab(tabbedPane, mode);
    }

    private void addServerTab(JBTabbedPane tabbedPane, JComponent description, EditionMode mode) {
        FormBuilder serverTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.server"));
        if (description != null) {
            serverTab.addComponent(description, 0);
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
    }

    private void addMappingsTab(JBTabbedPane tabbedPane, EditionMode mode) {
        FormBuilder mappingsTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.mappings"));
        this.mappingsPanel = new ServerMappingsPanel(mappingsTab, mode != EditionMode.EDIT_EXTENSION);
    }

    private void addDebugTab(JBTabbedPane tabbedPane, EditionMode mode) {
        if (mode == EditionMode.NEW_USER_DEFINED) {
            return;
        }
        FormBuilder debugTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.debug"));

        // Error reporting kind
        createErrorReportingCombo(debugTab);

        // Server trace
        debugTab.addLabeledComponent(LanguageServerBundle.message("language.server.trace"), serverTraceComboBox);

        if (mode == EditionMode.EDIT_EXTENSION) {
            // Debug port + suspend
            debugTab
                    .addLabeledComponent(LanguageServerBundle.message("language.server.debug.port"), debugPortField)
                    .addComponent(debugSuspendCheckBox);
        }
    }

    private void createErrorReportingCombo(FormBuilder builder) {

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

    private void addConfigurationTab(JBTabbedPane tabbedPane) {
        FormBuilder configurationTab = addTab(tabbedPane, LanguageServerBundle.message("language.server.tab.configuration"), false);

        JBTabbedPane configurationTabbedPane = new JBTabbedPane();
        configurationTab.addComponentFillVertically(configurationTabbedPane, 0);

        FormBuilder serverConfigurationTab = addTab(configurationTabbedPane, LanguageServerBundle.message("language.server.tab.configuration.server"), false);
        createConfigurationField(serverConfigurationTab);
        createInitializationOptionsTabField(serverConfigurationTab);

        FormBuilder clientConfigurationTab = addTab(configurationTabbedPane, LanguageServerBundle.message("language.server.tab.configuration.client"), false);
        createClientConfigurationField(clientConfigurationTab);
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
        serverName = new JBTextField();
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.serverName"), serverName);
    }

    private void createCommandLineField(FormBuilder builder) {
        commandLine = new CommandLineWidget();
        JBScrollPane scrollPane = new JBScrollPane(commandLine);
        scrollPane.setMinimumSize(new Dimension(JBUIScale.scale(600), JBUIScale.scale(100)));
        JLabel previewCommandLabel = createLabelForComponent("", scrollPane);
        updatePreviewCommand(commandLine, previewCommandLabel, project);
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.command"), scrollPane, true);
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

    private void createConfigurationField(FormBuilder builder) {

        // Create the hyperlink "Edit JSON Schema" / "Associate with JSON Schema".
        editJsonSchemaAction = new HyperlinkLabel(LanguageServerBundle.message("language.server.configuration.json.schema.associate"));
        builder.addLabeledComponent(LanguageServerBundle.message("language.server.configuration"), editJsonSchemaAction);
        editJsonSchemaAction.addHyperlinkListener(e-> {
            var dialog = new EditJsonSchemaDialog(project, configurationSchemaContent);
            dialog.show();
            if (dialog.isOK()) {
                // Associate the Server / Configuration editor with the new Json JSON content
                this.setConfigurationSchemaContent(dialog.getJsonSchemaContent());
            }
        });

        // Create the Server / Configuration Json editor
        configurationWidget = new SchemaBackedJsonTextField(project);
        builder.addComponentFillVertically(configurationWidget, 0);
    }

    private void createInitializationOptionsTabField(FormBuilder builder) {
        initializationOptionsWidget = new JsonTextField(project);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("language.server.initializationOptions"), initializationOptionsWidget);
    }

    private void createClientConfigurationField(FormBuilder builder) {
        clientConfigurationWidget = new JsonTextField(project);
        clientConfigurationWidget.setJsonFilename(LSPClientConfigurationJsonSchemaFileProvider.CLIENT_SETTINGS_JSON_FILE_NAME);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("language.server.configuration"), clientConfigurationWidget);
    }

    public JBTextField getServerName() {
        return serverName;
    }

    public EnvironmentVariablesComponent getEnvironmentVariables() {
        return environmentVariables;
    }

    public CommandLineWidget getCommandLine() {
        return commandLine;
    }

    public ServerMappingsPanel getMappingsPanel() {
        return mappingsPanel;
    }

    // TODO: Rename this to getConfigurationWidget()? getServerConfigurationWidget()?
    public SchemaBackedJsonTextField getConfiguration() {
        return configurationWidget;
    }

    public String getConfigurationSchemaContent() {
        return configurationSchemaContent;
    }

    public void setConfigurationSchemaContent(String configurationSchemaContent) {
        this.configurationSchemaContent = configurationSchemaContent;
        if (StringUtils.isNotBlank(configurationSchemaContent)) {
            getConfiguration().associateWithJsonSchema(configurationSchemaContent);
            editJsonSchemaAction.setHyperlinkText(LanguageServerBundle.message("language.server.configuration.json.schema.edit"));
        } else {
            getConfiguration().resetJsonSchema();
            editJsonSchemaAction.setHyperlinkText(LanguageServerBundle.message("language.server.configuration.json.schema.associate"));
        }
    }

    public JsonTextField getInitializationOptionsWidget() {
        return initializationOptionsWidget;
    }

    public JsonTextField getClientConfigurationWidget() {
        return clientConfigurationWidget;
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

    @Override
    public void dispose() {
        if (configurationWidget != null) {
            configurationWidget.resetJsonSchema();
        }
    }
}

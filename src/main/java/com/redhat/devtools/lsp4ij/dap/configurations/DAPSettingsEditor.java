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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebuggingType;
import com.redhat.devtools.lsp4ij.dap.templates.DAPTemplate;
import com.redhat.devtools.lsp4ij.dap.templates.DAPTemplateManager;
import com.redhat.devtools.lsp4ij.settings.ui.JsonTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static com.redhat.devtools.lsp4ij.LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID;

/**
 * Debug Adapter Protocol (DAP) settings editor.
 */
public class DAPSettingsEditor extends SettingsEditor<DAPRunConfiguration> {

    private final JPanel myPanel;
    private final @NotNull Project project;

    // Program settings
    private TextFieldWithBrowseButton workingDirectoryField;
    private TextFieldWithBrowseButton programField;

    // DAP server settings
    private ComboBox<DAPTemplate> templateCombo;
    private JTextField serverNameField;
    private TextFieldWithBrowseButton commandField;
    private JTextField waitForTraceField;

    private JRadioButton launchRadioButton;
    private JRadioButton attachRadioButton;
    private JsonTextField launchParametersField;
    private JsonTextField attachParametersField;
    private DAPTemplate currentTemplate;



    public DAPSettingsEditor(@NotNull Project project) {
        this.project = project;
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        JBTabbedPane tabbedPane = new JBTabbedPane();
        builder.addComponentFillVertically(tabbedPane, 0);

        // Program tab
        addProgramTab(tabbedPane);
        // Debug Adapter Protocol tab
        addDebugAdapterProtocolTab(tabbedPane, builder);

        myPanel = new JPanel(new BorderLayout());
        myPanel.add(builder.getPanel(), BorderLayout.CENTER);
    }

    private void addProgramTab(JBTabbedPane tabbedPane) {
        FormBuilder programTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.program.tab"));

        workingDirectoryField = new TextFieldWithBrowseButton();
        workingDirectoryField.addBrowseFolderListener("Select Working directory", null, null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
        programTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.cwd.field"), workingDirectoryField);

        programField = new TextFieldWithBrowseButton();
        programField.addBrowseFolderListener("Select Program", null, null,
                FileChooserDescriptorFactory.createSingleFileDescriptor());
        programTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.program.field"), programField);
    }

    private void addDebugAdapterProtocolTab(@NotNull JBTabbedPane tabbedPane,
                                            @NotNull FormBuilder builder) {
        FormBuilder dapTab = addTab(tabbedPane, DAPBundle.message("dap.settings.editor.dap.tab"));

        templateCombo = new ComboBox<>(new DefaultComboBoxModel<>(getDAPTemplates()));
        templateCombo.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList list,
                                  @Nullable DAPTemplate value,
                                  int index,
                                  boolean selected,
                                  boolean hasFocus) {
                if (value == null) {
                    setText("");
                } else {
                    setText(value.getName());
                }
            }
        });
        templateCombo.addItemListener(getTemplateComboListener());
        dapTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.template.field"), templateCombo);

        serverNameField = new JTextField();
        dapTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.name.field"), serverNameField);

        commandField = new TextFieldWithBrowseButton();
        commandField.addBrowseFolderListener("Select DAP File", null, null,
                FileChooserDescriptorFactory.createSingleFileDescriptor());
        dapTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.command.field"), commandField);

        waitForTraceField = new JTextField();
        dapTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.waitForTrace.field"), waitForTraceField);

        launchRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.server.debugging.launch.type"));
        attachRadioButton = new JRadioButton(DAPBundle.message("dap.settings.editor.server.debugging.attach.type"));

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.add(launchRadioButton);
        radioPanel.add(attachRadioButton);
        dapTab.addLabeledComponent(DAPBundle.message("dap.settings.editor.server.debugging.type.field"), radioPanel);

        final JBTabbedPane parametersTabbedPane = new JBTabbedPane();
        launchRadioButton.addActionListener( event -> {
            attachRadioButton.setSelected(false);
            parametersTabbedPane.setSelectedIndex(0);
        });
        attachRadioButton.addActionListener( event -> {
            launchRadioButton.setSelected(false);
            parametersTabbedPane.setSelectedIndex(1);
        });

        // Launch / Attach DAP parameters
        dapTab.addComponentFillVertically(parametersTabbedPane, 0);

        FormBuilder launchTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.server.parameters.launch.tab"));
        launchParametersField = new JsonTextField(project);
        launchTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.server.parameters.field"), launchParametersField);
        FormBuilder attachTab = addTab(parametersTabbedPane, DAPBundle.message("dap.settings.editor.server.parameters.attach.tab"));
        attachParametersField = new JsonTextField(project);
        attachTab.addLabeledComponentFillVertically(DAPBundle.message("dap.settings.editor.server.parameters.field"), attachParametersField);
    }

    /**
     * Create the template combo listener that handles item selection
     *
     * @return created ItemListener
     */
    private ItemListener getTemplateComboListener() {
    /* FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true,
            false, false, false, false);
    fileChooserDescriptor.setTitle(DAPBundle.message("dap.settings.editor.export.template.title"));
    fileChooserDescriptor.setDescription(DAPBundle.message("dap.settings.editor.export.template.description"));
*/
        return e -> {
            // Only trigger listener on selected items to avoid double triggering
            if (e.getStateChange() == ItemEvent.SELECTED) {
                DAPTemplate template = templateCombo.getItem();
  /*      if (template == DAPTemplate.NEW_TEMPLATE) {
          VirtualFile virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null);
          if (virtualFile != null) {
            try {
              template = DAPTemplateManager.getInstance().importDapTemplate(virtualFile);
              if (template == null) {
                showImportErrorNotification(DAPBundle.message("dap.settings.editor.import.template.error.description"));
              }
            } catch (IOException ex) {
              showImportErrorNotification(ex.getLocalizedMessage());
            }
          }
          // Reset template to None after trying to import a custom file
          templateCombo.setItem(DAPTemplate.NONE);
        }*/

                currentTemplate = template;
                //showInstructionButton.setEnabled(hasValidDescription(template));
                if (template != null) {
                    loadFromTemplate(template);
                }
            }
        };
    }

    private void showImportErrorNotification(String message) {
        Notification notification = new Notification(LSP4IJ_GENERAL_NOTIFICATIONS_ID,
                LanguageServerBundle.message("new.language.server.dialog.import.template.error.title"),
                message, NotificationType.ERROR);
        Notifications.Bus.notify(notification);
    }

    private void loadFromTemplate(DAPTemplate template) {
        // Update name
        serverNameField.setText(template.getName() != null ? template.getName() : "");

        // Update wait for trace
        waitForTraceField.setText(template.getWaitForTrace() != null ? template.getWaitForTrace() : "");

        // Update command
        String command = getCommandLine(template);
        commandField.setText(command);

        // Update mappings
        //var mappingsPanel = this.languageServerPanel.getMappingsPanel();
        //mappingsPanel.refreshMappings(template);

        // Update DAP parameters
        launchParametersField.setText(template.getLaunchConfiguration() != null ? template.getLaunchConfiguration() : "");
        launchParametersField.setCaretPosition(0);
        attachParametersField.setText(template.getAttachConfiguration() != null ? template.getAttachConfiguration() : "");
        attachParametersField.setCaretPosition(0);
    }

    private static String getCommandLine(DAPTemplate entry) {
        StringBuilder command = new StringBuilder();
        if (entry.getProgramArgs() != null) {
            if (!command.isEmpty()) {
                command.append(' ');
            }
            command.append(entry.getProgramArgs());
        }
        return command.toString();
    }


    @Override
    protected void resetEditorFrom(DAPRunConfiguration runConfiguration) {
        workingDirectoryField.setText(runConfiguration.getWorkingDirectory());
        programField.setText(runConfiguration.getProgram());
        commandField.setText(runConfiguration.getCommand());
        waitForTraceField.setText(runConfiguration.getWaitForTrace());
        boolean launchType = runConfiguration.getDebuggingType() == DebuggingType.LAUNCH;
        launchRadioButton.setSelected(launchType);
        attachRadioButton.setSelected(!launchType);
        launchParametersField.setText(runConfiguration.getLaunchParameters());
        attachParametersField.setText(runConfiguration.getAttachParameters());
    }

    @Override
    protected void applyEditorTo(@NotNull DAPRunConfiguration runConfiguration) {
        runConfiguration.setWorkingDirectory(workingDirectoryField.getText());
        runConfiguration.setProgram(programField.getText());
        runConfiguration.setCommand(commandField.getText());
        runConfiguration.setWaitForTrace(waitForTraceField.getText());
        runConfiguration.setDebuggingType(attachRadioButton.isSelected() ? DebuggingType.ATTACH : DebuggingType.LAUNCH);
        runConfiguration.setLaunchParameters(launchParametersField.getText());
        runConfiguration.setAttachParameters(attachParametersField.getText());
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

    private static DAPTemplate[] getDAPTemplates() {
        List<DAPTemplate> templates = new ArrayList<>();
        templates.add(DAPTemplate.NONE);
        //templates.add(DAPTemplate.NEW_TEMPLATE);
        templates.addAll(DAPTemplateManager.getInstance().getTemplates());
        return templates.toArray(new DAPTemplate[0]);
    }

    public @NotNull Project getProject() {
        return project;
    }
}
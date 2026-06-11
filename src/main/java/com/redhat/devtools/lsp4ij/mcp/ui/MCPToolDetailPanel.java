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
package com.redhat.devtools.lsp4ij.mcp.ui;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.mcp.MCPBundle;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPTool;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.awt.*;

/**
 * Panel to display tool details (name, description, schema).
 */
public class MCPToolDetailPanel {

    private final JPanel mainPanel;
    private JBLabel nameLabel;
    private JTextArea descriptionArea;
    private EditorEx schemaEditor;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MCPToolDetailPanel() {
        mainPanel = new JPanel(new BorderLayout());
        initUI();
    }

    private void initUI() {
        // Name field
        nameLabel = new JBLabel();
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

        // Description area
        descriptionArea = new JTextArea();
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setRows(3);
        descriptionArea.setBorder(JBUI.Borders.empty(5));
        JBScrollPane descScrollPane = new JBScrollPane(descriptionArea);
        descScrollPane.setPreferredSize(new Dimension(400, 80));

        // Schema editor (JSON)
        Document document = EditorFactory.getInstance().createDocument("");
        schemaEditor = (EditorEx) EditorFactory.getInstance().createEditor(document, null, FileTypes.UNKNOWN, true);
        schemaEditor.getSettings().setLineNumbersShown(true);
        schemaEditor.getSettings().setFoldingOutlineShown(false);

        // Build form
        FormBuilder formBuilder = FormBuilder.createFormBuilder()
                .setFormLeftIndent(10)
                .addComponent(nameLabel)
                .addVerticalGap(5)
                .addLabeledComponent(MCPBundle.message("mcp.tool.description"), descScrollPane)
                .addVerticalGap(10)
                .addLabeledComponent(MCPBundle.message("mcp.tool.schema"), schemaEditor.getComponent());

        JPanel formPanel = formBuilder.getPanel();
        formPanel.setBorder(JBUI.Borders.empty(10));

        mainPanel.add(formPanel, BorderLayout.NORTH);
    }

    public void showTool(@Nullable MCPToolBean toolBean) {
        if (toolBean == null) {
            clear();
            return;
        }

        nameLabel.setText(toolBean.getName());
        descriptionArea.setText(toolBean.getDescription());

        // Display schema as formatted JSON
        try {
            MCPTool tool = toolBean.getInstance();
            String schemaJson;
            if (tool != null) {
                schemaJson = gson.toJson(tool.getInputSchema());
            } else {
                schemaJson = "{}";
            }
            final String finalSchemaJson = schemaJson;
            WriteAction.run(() -> schemaEditor.getDocument().setText(finalSchemaJson));
        } catch (Exception e) {
            final String errorJson = "{\"error\": \"" + e.getMessage() + "\"}";
            WriteAction.run(() -> schemaEditor.getDocument().setText(errorJson));
        }
    }

    public void clear() {
        nameLabel.setText("");
        descriptionArea.setText("");
        WriteAction.run(() -> schemaEditor.getDocument().setText(""));
    }

    public JComponent getComponent() {
        return mainPanel;
    }
}

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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dialog to edit Json Schema.
 */
public class EditJsonSchemaDialog extends DialogWrapper {


    private final @NotNull Project project;
    private String jsonSchemaContent;
    private JsonTextField jsonSchemaWidget;

    public EditJsonSchemaDialog(@NotNull Project project, String jsonSchemaContent) {
        super(true);
        this.project = project;
        this.jsonSchemaContent = jsonSchemaContent;
        super.setTitle(LanguageServerBundle.message("edit.json.schema.dialog.title"));
        init();
        initValue();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        jsonSchemaWidget = new JsonTextField(project);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("edit.json.schema.dialog.schema"), jsonSchemaWidget);
        jsonSchemaWidget.addValidationHandler(hasErrors -> {
            super.setOKActionEnabled(!hasErrors);
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);

        Dimension size = new Dimension(700,300);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);

        return panel;
    }

    private void initValue() {
        if (StringUtils.isNotBlank(jsonSchemaContent)) {
            jsonSchemaWidget.setText(jsonSchemaContent);
        }
        jsonSchemaWidget.setCaretPosition(0);
    }

    /**
     * Returns the Json Schema content.
     *
     * @return the Json Schema content.
     */
    public String getJsonSchemaContent() {
        return jsonSchemaWidget.getText();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return jsonSchemaWidget.getComponent();
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
        return super.doValidateAll();
    }
}

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
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog to edit Json Schema.
 */
public class EditJsonSchemaDialog extends DialogWrapper {


    private final @NotNull Project project;
    private String jsonSchemaContent;
    private JsonTextField jsonSchemaWidget;

    protected EditJsonSchemaDialog(@NotNull Project project, String jsonSchemaContent) {
        super(true);
        this.project = project;
        this.jsonSchemaContent = jsonSchemaContent;
        super.setTitle(LanguageServerBundle.message("edit.json.schema.dialog.title"));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        FormBuilder builder = FormBuilder
                .createFormBuilder();

        jsonSchemaWidget = new JsonTextField(project);
        builder.addLabeledComponentFillVertically(LanguageServerBundle.message("edit.json.schema.dialog.schema"), jsonSchemaWidget);
        if (StringUtils.isNotBlank(jsonSchemaContent)) {
            jsonSchemaWidget.setText(jsonSchemaContent);
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void show() {
        // How to set the focus in the editor to see the cursor when dialog opens?
        jsonSchemaWidget.setCaretPosition(0);
        super.show();
    }

    /**
     * Returns the Json Schema content.
     *
     * @return the Json Schema content.
     */
    public String getJsonSchemaContent() {
        return jsonSchemaWidget.getText();
    }

}

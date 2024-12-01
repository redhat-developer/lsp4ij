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

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper for EditorTextField configured for JSON.
 */
public class JsonTextField extends JPanel {

    private static final String JSON_LANGUAGE_NAME = "JSON";
    private static final String DEFAULT_VALUE = "{}";

    private final EditorTextField editorTextField;

    public JsonTextField(@NotNull Project project) {
        // Create and initialize the editor text field
        EditorTextFieldProvider service = ApplicationManager.getApplication().getService(EditorTextFieldProvider.class);
        List<EditorCustomization> features = new ArrayList<>();
        ContainerUtil.addAllNotNull(features, Arrays.asList(
                MonospaceEditorCustomization.getInstance(),
                SoftWrapsEditorCustomization.ENABLED,
                SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization()
        ));
        Language jsonLanguage = Language.findLanguageByID(JSON_LANGUAGE_NAME);
        if (jsonLanguage == null) {
            jsonLanguage = PlainTextFileType.INSTANCE.getLanguage();
        }
        editorTextField = service.getEditorField(jsonLanguage, project, features);
        editorTextField.setOneLineMode(false);
        editorTextField.setText(DEFAULT_VALUE);

        // Add it to this panel
        setLayout(new BorderLayout());
        add(editorTextField, BorderLayout.CENTER);
    }

    // Proxy some simple accessors to the editor text field

    public void setText(@NotNull String text) {
        editorTextField.setText(text);
    }

    public @NotNull String getText() {
        return editorTextField.getText();
    }

    public void setCaretPosition(int position) {
        editorTextField.setCaretPosition(position);
    }
}

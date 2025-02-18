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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wrapper for EditorTextField configured for JSON.
 */
public class JsonTextField extends JPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonTextField.class);

    private static final String JSON_LANGUAGE_NAME = "JSON";
    private static final String DEFAULT_VALUE = "{}";

    private final EditorTextField editorTextField;
    private final List<Consumer<Boolean>> validationHandlers;

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

        validationHandlers = new ArrayList<>();
        editorTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                boolean hasErrors = hasErrors();
                for (var handler : validationHandlers) {
                    handler.accept(hasErrors);
                }
            }
        });
    }

    /**
     * Sets the editor's filename so that the correct JSON schema will be used for code completion and validation.
     *
     * @param jsonFilename the JSON file name
     */
    public void setJsonFilename(@NotNull String jsonFilename) {
        try {
            VirtualFile file = LSPIJUtils.getFile(editorTextField.getDocument());
            if (file != null) {
                file.rename(this, jsonFilename);
            } else {
                LOGGER.warn("Failed to rename the JSON text field file to '{}'.", jsonFilename);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to configure JSON text field for JSON schema '{}'.", jsonFilename, e);
        }
    }

    // Proxy some simple accessors to the editor text field

    public void setText(@Nullable String text) {
        editorTextField.setText(text != null ? text : DEFAULT_VALUE);
    }

    public @NotNull String getText() {
        return editorTextField.getText();
    }

    public void setCaretPosition(int position) {
        editorTextField.setCaretPosition(position);
    }

    public JComponent getComponent() {
        return editorTextField.getComponent();
    }

    public boolean hasErrors() {
        VirtualFile file = LSPIJUtils.getFile(editorTextField.getDocument());
        return PsiErrorElementUtil.hasErrors(getProject(), file);
    }

    public @NotNull Project getProject() {
        return editorTextField.getProject();
    }

    public void addValidationHandler(Consumer<Boolean> handler) {
        validationHandlers.add(handler);
    }

    @NotNull
    public Document getDocument() {
        return editorTextField.getDocument();
    }
}

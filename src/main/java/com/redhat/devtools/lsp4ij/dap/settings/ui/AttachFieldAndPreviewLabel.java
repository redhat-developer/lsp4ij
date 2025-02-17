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
package com.redhat.devtools.lsp4ij.dap.settings.ui;

import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Function;

/**
 * Preview label for attach address/port field.
 */
class AttachFieldAndPreviewLabel extends JPanel {

    private final @NotNull JBTextField textField;
    private final @NotNull Function<JBTextField, String> previewFunction;
    private final JLabel previewLabel;

    AttachFieldAndPreviewLabel(@NotNull JBTextField textField,
                               @NotNull Function<JBTextField, String> previewFunction) {
        this.textField = textField;
        this.previewFunction = previewFunction;
        super.setLayout(new FlowLayout(FlowLayout.LEFT));
        this.previewLabel = new JLabel();
        super.add(textField);
        super.add(previewLabel);
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePreview();
            }

        });
    }

    public void updatePreview() {
        String result = previewFunction.apply(textField);
        previewLabel.setText(result);
    }
}

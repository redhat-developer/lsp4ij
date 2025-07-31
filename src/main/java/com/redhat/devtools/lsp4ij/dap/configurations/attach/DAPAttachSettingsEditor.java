/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.attach;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.dap.configurations.options.AttachConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

/**
 * Settings editor to configure DAP client with "attach".
 *
 * @param <Settings> the "attach" configurable.
 */
public class DAPAttachSettingsEditor<Settings extends AttachConfigurable> extends SettingsEditor<Settings> {

    private final JPanel root;
    private final JTextField myAddress = new JTextField();
    private final JTextField myPort = new JTextField(Integer.toString(65535));

    public DAPAttachSettingsEditor(@NotNull Project project) {
        this.myPort.setMinimumSize(this.myPort.getPreferredSize());
        (new ComponentValidator(project)).withValidator(() -> {
            String pt = this.myPort.getText();
            if (StringUtil.isNotEmpty(pt)) {
                try {
                    int portValue = Integer.parseInt(pt);
                    return portValue >= 0 && portValue <= 65535 ? null : new ValidationInfo(ExecutionBundle.message("incorrect.port.range.set.value.between"), this.myPort);
                } catch (NumberFormatException var3) {
                    return new ValidationInfo(ExecutionBundle.message("port.value.should.be.a.number.between"), this.myPort);
                }
            } else {
                return null;
            }
        }).installOn(this.myPort);
        this.myPort.getDocument().addDocumentListener(new DocumentAdapter() {
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(DAPAttachSettingsEditor.this.myPort).ifPresent((v) -> v.revalidate());
            }
        });

        FormBuilder builder = FormBuilder
                .createFormBuilder();

        builder.addLabeledComponent("Port:", myPort);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(builder.getPanel(), BorderLayout.CENTER);
        root = panel;

    }

    @Override
    protected void resetEditorFrom(@NotNull Settings settings) {
        myPort.setText(settings.getAttachPort());
    }

    @Override
    protected void applyEditorTo(@NotNull Settings settings) throws ConfigurationException {
        settings.setAttachPort(myPort.getText());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return root;
    }
}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Mitja Leino <mitja.leino@hotmail.com> - Implement ValidatableConsoleWidget
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

import javax.swing.border.Border;
import java.util.List;

/**
 * Command line widget used to fill the command to start a language
 * server when creating a new or modifying an existing LS configuration
 */
public class CommandLineWidget extends JBTextArea implements ValidatableConsoleWidget {
    private final String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set");
    private final transient Border normalBorder;

    public CommandLineWidget() {
        super(5, 0);
        super.setLineWrap(true);
        super.setWrapStyleWord(true);
        super.setFont(JBFont.regular());
        super.getEmptyText().setText(LanguageServerBundle.message("language.server.command.emptyText"));
        this.normalBorder = this.getBorder();
    }

    @Override
    public void validate(List<ValidationInfo> validations) {
        boolean valid = true;
        if (getDocument() != null && getText().isBlank()) {
            setErrorBorder(this);
            valid = false;
            validations.add(new ValidationInfo(errorMessage, this));
        }
        if (valid) {
            this.setBorder(normalBorder);
        }
    }
}
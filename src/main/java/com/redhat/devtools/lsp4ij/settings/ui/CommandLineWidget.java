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
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

import javax.swing.border.Border;

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
        addListeners(this);
    }

    @Override
    public void validateInput() {
        if (isValid()) {
            this.setBorder(normalBorder);
        } else {
            setErrorBorder(this);
        }
    }

    @Override
    public ValidationInfo getValidationInfo() {
        if (isValid()) {
            return null;
        }
        return new ValidationInfo(errorMessage, this);
    }

    @Override
    public boolean isValid() {
        return getDocument() != null && !getText().isBlank();
    }
}
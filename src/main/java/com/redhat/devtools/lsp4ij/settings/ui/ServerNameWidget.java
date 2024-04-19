/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mitja Leino <mitja.leino@hotmail.com> - Initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

import javax.swing.border.Border;

/**
 * Server name widget that contains the server name when creating a new LS configuration
 */
public class ServerNameWidget extends JBTextField implements ValidatableConsoleWidget {
    private final String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set");
    private final transient Border originalBorder;

    public ServerNameWidget() {
        this.originalBorder = this.getBorder();
        addListeners(this);
    }

    @Override
    public void validateInput() {
        if (isValid()) {
            this.setBorder(originalBorder);
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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBTextField;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

import javax.swing.border.Border;

public class ServerNameWidget extends JBTextField implements ValidatableConsoleWidget {
    private boolean isValid = true;
    private final String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set");
    private final transient Border originalBorder;

    public ServerNameWidget() {
        this.originalBorder = this.getBorder();
        addListeners(this);
    }

    @Override
    public void validateInput() {
        if (getText().isBlank()) {
            isValid = false;
            setErrorBorder(this);
        } else {
            isValid = true;
            this.setBorder(originalBorder);
        }
    }

    public ValidationInfo getValidationInfo() {
        if (!isValid) {
            return new ValidationInfo(errorMessage, this);
        }
        return null;
    }
}

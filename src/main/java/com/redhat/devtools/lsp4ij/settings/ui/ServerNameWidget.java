package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBTextField;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;

public class ServerNameWidget extends JBTextField {
    private boolean isValid = true;
    private final String errorMessage = LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set");
    private final transient Border normalBorder;

    public ServerNameWidget() {
        this.normalBorder = this.getBorder();
        this.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull final DocumentEvent e){
                validateInput();
            }
        });
        validateInput();
    }

    public void validateInput() {
        if (getText().isBlank()) {
            isValid = false;
            ValidatableConsoleWidget.setErrorBorder(this);
        } else {
            isValid = true;
            this.setBorder(normalBorder);
        }
    }

    public ValidationInfo getValidationInfo() {
        if (!isValid) {
            return new ValidationInfo(errorMessage, this);
        }
        return null;
    }
}

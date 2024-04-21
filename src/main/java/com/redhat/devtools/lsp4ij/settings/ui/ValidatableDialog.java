package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

public abstract class ValidatableDialog extends DialogWrapper {
    public ValidatableDialog(Project project) {
        super(project);
    }

    public void refreshValidation() {
        super.initValidation();
    }

    @Override
    protected boolean continuousValidation() {
        return false;
    }
}

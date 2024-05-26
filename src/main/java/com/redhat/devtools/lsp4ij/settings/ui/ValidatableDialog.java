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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

/**
 * Shareable component for shared validations using DialogWrapper
 */
public abstract class ValidatableDialog extends DialogWrapper {
    protected ValidatableDialog(Project project) {
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

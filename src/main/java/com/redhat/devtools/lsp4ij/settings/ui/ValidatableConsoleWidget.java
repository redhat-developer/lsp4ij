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
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.util.List;
import java.awt.*;

/**
 * A shared interface meant to simplify creating validatable components used in
 * NewLanguageServerDialog and LanguageServerView (LSP console)
 */
public interface ValidatableConsoleWidget {
    /**
     * Set a common error border for the widget
     * @param jComponent interface implementor (e.g. setErrorBorder(this);)
     */
    default void setErrorBorder(JComponent jComponent) {
        Color color = JBColor.red;
        color = color.darker();
        jComponent.setBorder(JBUI.Borders.customLine(color, 1));
    }

    void validate(List<ValidationInfo> validations);
}
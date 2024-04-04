/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui.refactoring;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.RefactoringOnFileOperationsKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * View for language server "Refactoring" which display:
 *
 * <ul>
 *     <li>a combo to customize the on file create|delete|rename kind</li>
 * </ul>
 */
public class LanguageServerRefactoringView implements Disposable {

    private final JPanel myMainPanel;

    private final ComboBox<RefactoringOnFileOperationsKind> refactoringOnFileOperationsCombo = new ComboBox<>(new DefaultComboBoxModel<>(RefactoringOnFileOperationsKind.values()));

    public LanguageServerRefactoringView() {
        this.myMainPanel = JBUI.Panels
                .simplePanel(10, 10)
                .addToCenter(createSettings());
    }

    private JPanel createSettings() {
        refactoringOnFileOperationsCombo.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
            String text = LanguageServerBundle.message("language.servers.refactoring.on.file.operations.interactive");
            if (value != null) {
                switch (value) {
                    case interactive -> text = LanguageServerBundle.message("language.servers.refactoring.on.file.operations.interactive");
                    case apply -> text = LanguageServerBundle.message("language.servers.refactoring.on.file.operations.apply");
                    case skip -> text = LanguageServerBundle.message("language.servers.refactoring.on.file.operations.skip");
                }
            }
            label.setText(text);
        }));
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(LanguageServerBundle.message("language.servers.refactoring.on.file.operations"), refactoringOnFileOperationsCombo)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    @NotNull
    public RefactoringOnFileOperationsKind getRefactoringOnFileOperationsKind() {
        return (RefactoringOnFileOperationsKind) refactoringOnFileOperationsCombo.getSelectedItem();
    }

    public void setRefactoringOnFileOperationsKind(@NotNull RefactoringOnFileOperationsKind kind) {
        refactoringOnFileOperationsCombo.setSelectedItem(kind);
    }

    @Override
    public void dispose() {

    }

}

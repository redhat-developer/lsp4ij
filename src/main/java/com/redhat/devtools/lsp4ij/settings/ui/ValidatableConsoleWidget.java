package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

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

    /**
     * Add listeners that handle input validation. The first instance of validation should run on focus
     * and then on change
     * @param jComponent interface implementor (e.g. addListeners(this);)
     */
    default void addListeners(JComponent jComponent) {
        jComponent.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                validateInput();
                super.focusGained(e);
            }
        });

        DocumentAdapter adapter = new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                validateInput();
            }
        };
        if (jComponent instanceof JBTextField jbTextField) {
            jbTextField.getDocument().addDocumentListener(adapter);
        } else if (jComponent instanceof JBTextArea jbTextArea) {
            jbTextArea.getDocument().addDocumentListener(adapter);
        }
    }

    /**
     * Overridable method that implements the field validation
     */
    void validateInput();
}
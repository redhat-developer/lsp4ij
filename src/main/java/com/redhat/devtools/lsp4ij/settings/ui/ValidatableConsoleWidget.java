package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class ValidatableConsoleWidget {
    private ValidatableConsoleWidget() {
        // Prevent init
    }

    public static void setErrorBorder(JComponent jComponent) {
        Color color = JBColor.red;
        color = color.darker();
        jComponent.setBorder(JBUI.Borders.customLine(color, 1));
    }
}
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

public class CommandLineWidget extends JBTextArea {

    public CommandLineWidget() {
        super(5, 0);
        super.setLineWrap(true);
        super.setWrapStyleWord(true);
        // commandField.setBorder(new JBEmptyBorder(3, 5, 3, 5));
        super.setFont(JBFont.regular());
        super.getEmptyText().setText(LanguageServerBundle.message("new.language.server.dialog.command.emptyText"));
    }
}

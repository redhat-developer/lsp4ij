/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

/**
 * Language server LSP 'Initialize Options' widget used to fill the LSP 'Initialization Options' expected by the language server.
 */
public class LanguageServerInitializationOptionsWidget extends JBTextArea {

    public LanguageServerInitializationOptionsWidget() {
        super(5, 0);
        super.setLineWrap(true);
        super.setWrapStyleWord(true);
        super.setFont(JBFont.regular());
        super.getEmptyText().setText(LanguageServerBundle.message("language.server.initializationOptions.emptyText"));
    }
}

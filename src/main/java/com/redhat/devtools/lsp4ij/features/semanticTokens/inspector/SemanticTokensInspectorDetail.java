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
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;

import javax.swing.*;

/**
 * Semantic tokens 'detail' panel show on the left of the semantic tokens inspector view.
 */
public class SemanticTokensInspectorDetail extends SimpleToolWindowPanel implements Disposable {

    private final SemanticTokensInspectorToolWindowPanel panel;

    private final JBCheckBox showTextAttributesCheckBox = new JBCheckBox(LanguageServerBundle.message("lsp.semantic.tokens.inspector.show.text.attributes"));
    private final JBCheckBox showTokenTypeCheckBox = new JBCheckBox(LanguageServerBundle.message("lsp.semantic.tokens.inspector.show.token.type"));
    private final JBCheckBox showTokenModifiersCheckBox = new JBCheckBox(LanguageServerBundle.message("lsp.semantic.tokens.inspector.show.token.modifiers"));

    public SemanticTokensInspectorDetail(SemanticTokensInspectorToolWindowPanel panel) {
        super(true, false);
        this.panel = panel;
        this.setContent(createUI());
    }

    private JPanel createUI() {
        FormBuilder builder = FormBuilder
                .createFormBuilder()
                .setFormLeftIndent(10);
        builder.addComponent(showTextAttributesCheckBox);
        builder.addComponent(showTokenTypeCheckBox);
        builder.addComponent(showTokenModifiersCheckBox);
        builder
                .addComponentFillVertically(new JPanel(), 50);

        showTextAttributesCheckBox.addChangeListener(e -> panel.refresh());
        showTokenTypeCheckBox.addChangeListener(e -> panel.refresh());
        showTokenModifiersCheckBox.addChangeListener(e -> panel.refresh());

        showTextAttributesCheckBox.setSelected(true);
        return builder.getPanel();
    }

    public boolean isShowTextAttributes() {
        return showTextAttributesCheckBox.isSelected();
    }

    public boolean isShowTokenType() {
        return showTokenTypeCheckBox.isSelected();
    }

    public boolean isShowTokenModifiers() {
        return showTokenModifiersCheckBox.isSelected();
    }

    @Override
    public void dispose() {

    }
}

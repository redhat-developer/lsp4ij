/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *     Mitja Leino <mitja.leino@hotmail.com> - Render markdown using flexmark and only support close action
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPaneKt;
import com.intellij.ui.ScrollPaneFactory;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * Dialog used to show some instructions (install nodejs, etc) which explains
 * how to use a given template language server.
 */
public class ShowInstructionDialog extends DialogWrapper {

    private @NotNull  final LanguageServerTemplate template;

    protected ShowInstructionDialog(LanguageServerTemplate template, @Nullable Project project) {
        super(project);
        super.setTitle(LanguageServerBundle.message("template.show.instruction.dialog.title", template.getName()));
        this.template = template;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        var descriptionBrowser = new DescriptionEditorPane();
        final var descriptionScrollPane = ScrollPaneFactory.createScrollPane(descriptionBrowser);
        descriptionScrollPane.setBorder(null);

        // Convert Markdown to HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        Document document = parser.parse(template.getDescription());
        String html = renderer.render(document);

        DescriptionEditorPaneKt.readHTML(descriptionBrowser, html);
        descriptionBrowser.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.browse(e.getURL());
            }
        });
        descriptionBrowser.setPreferredSize(new Dimension(800, 400));
        return descriptionScrollPane;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
    }

    @Override
    protected void createDefaultActions() {
        super.createDefaultActions();
        getOKAction().putValue(Action.NAME, "Close");
    }
}
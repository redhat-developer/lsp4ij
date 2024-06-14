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
 *     Mitja Leino <mitja.leino@hotmail.com> - Covert markdown to html and only support close action
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane;
import com.intellij.profile.codeInspection.ui.DescriptionEditorPaneKt;
import com.intellij.ui.ScrollPaneFactory;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

/**
 * Dialog used to show some instructions (install nodejs, etc) which explains
 * how to use a given template language server.
 */
public class ShowInstructionDialog extends DialogWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShowInstructionDialog.class);
    private final @NotNull LanguageServerTemplate template;
    private final @NotNull Project project;

    protected ShowInstructionDialog(LanguageServerTemplate template, @NotNull Project project) {
        super(project);
        this.project = project;
        super.setTitle(LanguageServerBundle.message("template.show.instruction.dialog.title", template.getName()));
        this.template = template;
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        var descriptionBrowser = new DescriptionEditorPane();
        final var descriptionScrollPane = ScrollPaneFactory.createScrollPane(descriptionBrowser);
        descriptionScrollPane.setBorder(null);

        String docContent = template.getDescription();
        String description;

        try {
            description = MarkdownConverter.getInstance(project).toHtml(docContent);
        } catch (Exception e) {
            description = docContent;
            LOGGER.warn("Error while converting MarkDown language server template documentation to HTML", e);
        }

        DescriptionEditorPaneKt.readHTML(descriptionBrowser, description);
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
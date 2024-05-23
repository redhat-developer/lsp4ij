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
package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


/**
 * Export one or more language servers to a zip file.
 */
public class ExportServerAction extends AnAction {
    private final List<LanguageServerDefinition> languageServerDefinitions;

    public ExportServerAction(@NotNull List<LanguageServerDefinition> languageServerDefinitions) {
        this.languageServerDefinitions = languageServerDefinitions;
        if (this.languageServerDefinitions.size() == 1) {
            getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.export.server.text"));
            getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.export.server.description"));
        } else {
            getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.export.servers.text"));
            getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.export.servers.description"));
        }
        getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Export);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
        FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor(
                LanguageServerBundle.message("action.lsp.console.explorer.export.servers.zip.save.title"), LanguageServerBundle.message("action.lsp.console.explorer.export.servers.zip.save.description"));
        FileSaverDialog fileSaverDialog = fileChooserFactory.createSaveFileDialog(fileSaverDescriptor, e.getProject());

        String currentDate = getCurrentDate();
        VirtualFileWrapper fileWrapper = fileSaverDialog.save("ls4ij-export" + currentDate + ".zip");
        if (fileWrapper != null) {
            VirtualFile exportZip = fileWrapper.getVirtualFile(true);
            if (exportZip != null) {
                LanguageServerTemplateManager.getInstance().exportLsTemplates(exportZip, languageServerDefinitions);
            }
        }
    }

    private String getCurrentDate() {
        LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return currentDate.format(formatter);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
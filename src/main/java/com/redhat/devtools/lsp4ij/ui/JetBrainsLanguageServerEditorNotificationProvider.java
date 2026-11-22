/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerListConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Warns, above the editor of a file, when the file is served both by a language server of LSP4IJ and by one of
 * IntelliJ's own LSP client, from its <i>Settings | Languages &amp; Frameworks | Language Servers</i> (2026.3 and
 * later, see {@link JetBrainsLanguageServers}). Two clients then give each result twice: diagnostics, completion items,
 * hovers and so on (#1933).
 */
public class JetBrainsLanguageServerEditorNotificationProvider implements EditorNotificationProvider, DumbAware {

    /**
     * Key used in {@link PropertiesComponent} to determine if the notification should be suppressed.
     */
    static final @NonNls String DISABLE_KEY = "lsp.jetbrains.servers.notification.disabled";

    private final @NotNull Function<Project, List<JetBrainsLanguageServers.Server>> jetBrainsServers;

    public JetBrainsLanguageServerEditorNotificationProvider() {
        this(JetBrainsLanguageServers::getServers);
    }

    JetBrainsLanguageServerEditorNotificationProvider(@NotNull Function<Project, List<JetBrainsLanguageServers.Server>> jetBrainsServers) {
        this.jetBrainsServers = jetBrainsServers;
    }

    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile file) {
        if (PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)
                || !LanguageServersRegistry.getInstance().isFileSupported(file, project)) {
            return null;
        }
        List<String> jetBrains = jetBrainsServers.apply(project).stream()
                .filter(server -> server.enabled() && server.matches(file.getName()))
                .map(JetBrainsLanguageServers.Server::name)
                .toList();
        if (jetBrains.isEmpty()) {
            return null;
        }
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        if (psiFile == null) {
            return null;
        }
        List<String> lsp4ij = LanguageServiceAccessor.getInstance(project).getMatchedLanguageServerDefinitions(psiFile).stream()
                .map(LanguageServerDefinition::getDisplayName)
                .toList();
        if (lsp4ij.isEmpty()) {
            return null;
        }
        String text = LanguageServerBundle.message("language.server.jetbrains.duplicate.text", namesOf(lsp4ij), namesOf(jetBrains));
        return fileEditor -> createPanel(text, fileEditor, project);
    }

    private static @NotNull String namesOf(@NotNull List<String> servers) {
        return servers.stream().sorted().map(name -> "'" + name + "'").collect(Collectors.joining(", "));
    }

    private static @NotNull EditorNotificationPanel createPanel(@NotNull String text,
                                                                @NotNull FileEditor fileEditor,
                                                                @NotNull Project project) {
        var panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning);
        panel.setText(text);
        panel.createActionLabel(LanguageServerBundle.message("language.server.jetbrains.duplicate.lsp4ij"), () -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, LanguageServerListConfigurable.class);
            // The settings dialog is modal: one of the servers may have been disabled
            EditorNotifications.getInstance(project).updateAllNotifications();
        });
        panel.createActionLabel(LanguageServerBundle.message("language.server.jetbrains.duplicate.jetbrains"), () -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(project,
                    configurable -> configurable instanceof SearchableConfigurable page
                            && JetBrainsLanguageServers.CONFIGURABLE_ID.equals(page.getId()),
                    null);
            EditorNotifications.getInstance(project).updateAllNotifications();
        });
        panel.createActionLabel(LanguageServerBundle.message("language.server.jetbrains.duplicate.dismiss"), () -> {
            PropertiesComponent.getInstance().setValue(DISABLE_KEY, true);
            EditorNotifications.getInstance(project).updateAllNotifications();
        });
        return panel;
    }
}

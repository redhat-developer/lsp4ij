/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LSPIJEditorUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.launching.ui.NewLanguageServerDialog;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * {@code InstallLanguageServerEditorNotificationProvider} is an {@link EditorNotificationProvider}
 * that displays a notification panel above an editor when a file is not currently supported by any
 * installed Language Server, but matching templates are available.
 * <p>
 * This provider allows users to quickly install or configure a Language Server (via LSP4IJ)
 * based on detected file type or patterns.
 */
public class LanguageServerSuggestionEditorNotificationProvider implements EditorNotificationProvider {

    /**
     * Key used in {@link PropertiesComponent} to determine if the notification should be suppressed.
     */
    private static final @NonNls String DISABLE_KEY = "lsp.install.server.notification.disabled";

    /**
     * Checks whether a notification should be shown for a given file in the editor.
     * If the file is not currently handled by any registered Language Server and matching
     * templates exist, a panel suggesting installation is provided.
     *
     * @param project     the current project
     * @param virtualFile the file currently opened in an editor
     * @return a function that provides the notification component for the editor,
     * or {@code null} if no notification should be shown
     */
    @Override
    public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(
            @NotNull Project project,
            @NotNull VirtualFile virtualFile) {

        // Do not show notification if user dismissed it
        if (PropertiesComponent.getInstance().isTrueValue(DISABLE_KEY)) {
            return null;
        }

        // If the registered file type for the given file is not AbstractFileType, PlainTextFileType, or TextMateFileType,
        // LSP4IJ should assume that the file is already handled in a first-class manner and the user should not be prompted.
        PsiFile file = LSPIJUtils.getPsiFile(virtualFile, project);
        if (file == null || !(LSPIJEditorUtils.isPlainTextFile(file) || LSPIJEditorUtils.isSupportedAbstractFileTypeOrTextMateFile(file))) {
            return null;
        }

        // Collect language server templates which match the file.
        List<LanguageServerTemplate> matchedTemplates = new ArrayList<>();
        Set<String> patterns = new HashSet<>();
        for (var template : LanguageServerTemplateManager.getInstance().getTemplates()) {
            String filePattern = template.getMatchedMapping(virtualFile, project);
            if (filePattern != null && template.isPromotable() && !isServerExists(template.getId())) {
                matchedTemplates.add(template);
                patterns.add(filePattern);
            }
        }

        // No language server templates which match the file, don't show the LSP4IJ editor notification
        if (matchedTemplates.isEmpty()) {
            return null;
        }

        // Show the LSP4IJ editor notification to suggest installation of matched templates.
        return fileEditor -> buildPanel(String.join(", ", patterns), matchedTemplates, fileEditor, project);
    }

    private boolean isServerExists(@Nullable String templateId) {
        if (templateId == null) {
            return false;
        }
        for(var serverDefinition : LanguageServersRegistry.getInstance().getServerDefinitions()) {
            if (serverDefinition instanceof UserDefinedLanguageServerDefinition userDefinedLanguageServerDefinition) {
                if (templateId.equals(userDefinedLanguageServerDefinition.getTemplateId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Builds an editor notification panel suggesting installation of one or more
     * Language Server templates that match the current file type or pattern.
     *
     * @param filePattern      textual description of matched patterns (e.g., file extensions)
     * @param matchedTemplates list of {@link LanguageServerTemplate} matching the current file
     * @param fileEditor       the file editor
     * @param project          the current project
     * @return an {@link EditorNotificationPanel} to be displayed in the editor
     */
    private EditorNotificationPanel buildPanel(@NotNull String filePattern,
                                               @NotNull List<LanguageServerTemplate> matchedTemplates,
                                               @NotNull FileEditor fileEditor,
                                               @NotNull Project project) {

        var panel = new EditorNotificationPanel(UIUtil.getEditorPaneBackground(), EditorNotificationPanel.Status.Promo);
        panel.setText(LanguageServerBundle.message("language.server.suggest.install.title", filePattern));

        // Add one install button per matched template
        matchedTemplates.forEach(template -> {
            panel.createActionLabel(LanguageServerBundle.message("language.server.suggest.install.template", template.getName()), () -> {
                NewLanguageServerDialog dialog = new NewLanguageServerDialog(project);
                dialog.loadFromTemplate(template);
                dialog.show();
                // Refresh notifications to hide it
                EditorNotifications.getInstance(project)
                        .updateNotifications(fileEditor.getFile());
            });
        });

        // Dismiss button (can be enhanced to persist dismissal if needed)
        panel.createActionLabel(LanguageServerBundle.message("language.server.suggest.install.dismiss"), () -> {
            // Store preference to disable future notifications
            PropertiesComponent.getInstance().setValue(DISABLE_KEY, true);
            // Refresh notifications to hide the panel
            EditorNotifications.getInstance(project)
                    .updateAllNotifications();
        });

        return panel;
    }

}

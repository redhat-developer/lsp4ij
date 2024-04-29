package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

public class EditServerAction extends AnAction {
    private final LanguageServerDefinition languageServerDefinition;

    public EditServerAction(LanguageServerDefinition languageServerDefinition) {
        this.languageServerDefinition = languageServerDefinition;
        getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.edit.server.text"));
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.edit.server.description"));
        getTemplatePresentation().setIcon(AllIcons.Actions.Edit);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = ProjectManager.getInstance().getOpenProjects()[0];
        UserDefinedLanguageServerSettings settings = UserDefinedLanguageServerSettings.getInstance(project);
        settings.setOpenNode(languageServerDefinition.getDisplayName());
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "configurable.group.language.language.servers");
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
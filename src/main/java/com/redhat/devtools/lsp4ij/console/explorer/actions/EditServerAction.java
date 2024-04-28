package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.LanguageServerListConfigurable;
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
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "Languages & Frameworks.language.servers");
        Configurable[] configurables = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true).getConfigurables();
        for (Configurable c : configurables) {
            if (c.getDisplayName().equals("configurable.group.language")) {
                System.out.println(c.getDisplayName());
                for (Configurable s : c.get)
                    System.out.println("We found him!");
                    languageServerListConfigurable.selectNodeInTree("dsa");
                }
            }
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
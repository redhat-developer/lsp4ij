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
package com.redhat.devtools.lsp4ij.console.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.LanguageServerView;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * Action to update the proper language server settings and language server definition
 * from the UI language server view fields.
 */
public class ApplyLanguageServerSettingsAction extends AnAction {
    public static final String ACTION_TOOLBAR_COMPONENT_NAME = "ActionToolbarComponent";
    private final LanguageServerView languageServerView;
    private Balloon saveTipBalloon;
    private boolean hasSaveTipBalloonShown = false;

    public ApplyLanguageServerSettingsAction(LanguageServerView languageServerView) {
        this.languageServerView = languageServerView;
        final String message = LanguageServerBundle.message("action.lsp.detail.apply.text");
        getTemplatePresentation().setDescription(message);
        getTemplatePresentation().setText(message);
        getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.detail.apply.description"));
        getTemplatePresentation().setIcon(AllIcons.Actions.MenuSaveall);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        languageServerView.apply();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean modified = languageServerView.isModified();
        var project = e.getProject();

        if (project != null) {
            boolean showSaveTipOnConfigurationChange = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(project).showSaveTipOnConfigurationChange();
            // Only show the tooltip once per configuration change
            if (modified && !hasSaveTipBalloonShown && showSaveTipOnConfigurationChange) {
                hasSaveTipBalloonShown = true;
                showBalloon(project);
            }
            // If configuration is returned to match the unmodified state, reset the tooltip to be shown again
            if (!modified) {
                hasSaveTipBalloonShown = false;
            }
        }

        e.getPresentation().setEnabled(modified);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Create and show a balloon to draw attention to the save button when modifying
     * configurations in the LSP console
     * @param project passed on to the panel creation to create the button's action listener
     */
    private void showBalloon(@NotNull Project project) {
        var jbPanel = createBalloonPanel(project);

        BalloonBuilder builder = JBPopupFactory.getInstance()
                .createBalloonBuilder(jbPanel)
                .setFadeoutTime(10000) // How many ms the balloon is shown for
                .setHideOnAction(false);

        // Have an instance reference to hide the balloon with the button
        this.saveTipBalloon = builder.createBalloon();

        var components = languageServerView.getComponent().getComponents();
        Optional<Component> actionToolbarComponent = Arrays.stream(components)
                .filter(c -> ACTION_TOOLBAR_COMPONENT_NAME.equals(c.getName()))
                .findFirst();

        if (actionToolbarComponent.isEmpty()) {
            return;
        }
        Component applyComponent = ((ActionToolbarImpl) actionToolbarComponent.get()).getComponent(0);

        // Move the position by 1/2 of the component width, because the icon is not centered
        Point point = new Point(applyComponent.getWidth()/2 ,0);
        RelativePoint displayPoint = new RelativePoint(applyComponent, point);

        // Use invokeLater to prevent the balloon from resizing when it is shown
        ApplicationManager.getApplication().invokeLater(() -> this.saveTipBalloon.show(displayPoint, Balloon.Position.above));
    }

    /**
     * Create the balloon panel that contains the save tip and disable button stacked on top of each other
     * @param project necessary for updating the settings
     * @return the created JBPanel
     */
    private @NotNull JBPanel<JBPanel> createBalloonPanel(Project project) {
        var jbPanel = new JBPanel<>();
        jbPanel.setLayout(new BoxLayout(jbPanel, BoxLayout.Y_AXIS));
        // For some reason the HyperlinkLabel is indented by a single space
        JBLabel jbLabel = new JBLabel(" " + LanguageServerBundle.message("action.lsp.detail.apply.balloon"));
        HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(LanguageServerBundle.message("action.lsp.detail.apply.balloon.disable"));
        hyperlinkLabel.addHyperlinkListener(e -> {
            UserDefinedLanguageServerSettings.getInstance(project).showSaveTipOnConfigurationChange(false);
            this.saveTipBalloon.hide();
        });

        jbPanel.add(jbLabel);
        jbPanel.add(hyperlinkLabel);
        return jbPanel;
    }
}

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
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.LanguageServerView;
import com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Action to update the proper language server settings and language server definition
 * from the UI language server view fields.
 */
public class ApplyLanguageServerSettingsAction extends AnAction {
    public static final String ACTION_TOOLBAR_COMPONENT_NAME = "ActionToolbarComponent";
    private final LanguageServerView languageServerView;
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
        boolean isShown = languageServerView.isSaveTipShown();
        boolean isSaveTipBalloonDisabled = com.redhat.devtools.lsp4ij.settings.UserDefinedLanguageServerSettings.getInstance(Objects.requireNonNull(e.getProject())).isSaveTipBalloonDisabled();
        // Only show the tooltip once per configuration change
        if (modified && !isShown && !isSaveTipBalloonDisabled) {
            showBalloon();
        }
        // If configuration is returned to match the unmodified state, reset the tooltip to be shown again
        if (!modified) {
            languageServerView.isSaveTipShown(false);
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
     */
    private void showBalloon() {
        languageServerView.isSaveTipShown(true);
        JBLabel jbPanel = new JBLabel(LanguageServerBundle.message("action.lsp.detail.apply.balloon"));

        BalloonBuilder builder = JBPopupFactory.getInstance()
                .createBalloonBuilder(jbPanel)
                .setFadeoutTime(800) // How many ms the balloon is shown for
                .setHideOnAction(false);

        Balloon balloon = builder.createBalloon();

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
        ApplicationManager.getApplication().invokeLater(() -> balloon.show(displayPoint, Balloon.Position.above));
    }
}

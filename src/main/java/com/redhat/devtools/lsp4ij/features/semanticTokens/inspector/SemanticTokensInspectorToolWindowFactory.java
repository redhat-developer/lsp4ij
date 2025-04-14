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

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Semantic Tokens Inspector factory.
 *
 * @author Angelo ZERR
 */
public class SemanticTokensInspectorToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SemanticTokensInspectorToolWindowPanel semanticTokensInspectorView = new SemanticTokensInspectorToolWindowPanel(project);
        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.getFactory().createContent(semanticTokensInspectorView,
                LanguageServerBundle.message("lsp.semantic.tokens.inspector.title"), false);
        content.setDisposer(semanticTokensInspectorView);
        contentManager.addContent(content);

        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager toolWindowManager, @NotNull ToolWindowManagerEventType changeType) {
                if (changeType == ToolWindowManagerEventType.HideToolWindow && !toolWindowManager.isStripeButtonShow(toolWindow)) {
                    toolWindow.setAvailable(false);
                }
            }
        });
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return false;
    }
}

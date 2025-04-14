/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.ide.actions.ActivateToolWindowAction;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.ui.LSPToolWindowId;
import org.jetbrains.annotations.NotNull;

/**
 * Activate "Semantic Tokens Inspector" action.
 */
public class ActivateSemanticTokensInspectorToolWindowAction extends ActivateToolWindowAction {

    protected ActivateSemanticTokensInspectorToolWindowAction() {
        super(LSPToolWindowId.SEMANTIC_TOKENS_INSPECTOR);
        getTemplatePresentation().setText(LanguageServerBundle.message("lsp.semantic.tokens.inspector.title"));
    }

    @Override
    protected boolean hasEmptyState(@NotNull Project project) {
        return true;
    }
}

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

package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.EditorBehaviorFeature;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientConfigurationEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User-defined editor behavior feature.
 */
public class UserDefinedEditorBehaviorFeature extends EditorBehaviorFeature {

    public UserDefinedEditorBehaviorFeature(@NotNull LSPClientFeatures clientFeatures) {
        super(clientFeatures);
    }

    @Nullable
    private ClientConfigurationEditorSettings getEditorSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.editor : null;
    }

    @Override
    public boolean isEnableStringLiteralImprovements(@NotNull PsiFile file) {
        ClientConfigurationEditorSettings editorSettings = getEditorSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (editorSettings == null) || editorSettings.enableStringLiteralImprovements;
    }

    @Override
    public boolean isEnableStatementTerminatorImprovements(@NotNull PsiFile file) {
        ClientConfigurationEditorSettings editorSettings = getEditorSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (editorSettings == null) || editorSettings.enableStatementTerminatorImprovements;
    }

    @Override
    public boolean isEnableEnterBetweenBracesFix(@NotNull PsiFile file) {
        ClientConfigurationEditorSettings editorSettings = getEditorSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (editorSettings == null) || editorSettings.enableEnterBetweenBracesFix;
    }

    @Override
    public boolean isEnableTextMateNestedBracesImprovements(@NotNull PsiFile file) {
        ClientConfigurationEditorSettings editorSettings = getEditorSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (editorSettings == null) || editorSettings.enableTextMateNestedBracesImprovements;
    }

    @Override
    public boolean isEnableSemanticTokensFileViewProvider(@NotNull PsiFile file) {
        ClientConfigurationEditorSettings editorSettings = getEditorSettings();
        // Note that this defaults to enabled for user-defined language server definitions
        return (editorSettings == null) || editorSettings.enableSemanticTokensFileViewProvider;
    }
}

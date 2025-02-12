/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds client-side configuration features.
 */
public class UserDefinedClientFeatures extends LSPClientFeatures {

    public UserDefinedClientFeatures() {
        super();

        // Use the extended feature implementations
        setCompletionFeature(new UserDefinedCompletionFeature());
        setFormattingFeature(new UserDefinedFormattingFeature());
        setWorkspaceSymbolFeature(new UserDefinedWorkspaceSymbolFeature());
        setEditorBehaviorFeature(new UserDefinedEditorBehaviorFeature(this));
    }

    public boolean isCaseSensitive(@NotNull PsiFile file) {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.caseSensitive : super.isCaseSensitive(file);
    }

    @Override
    @Nullable
    public String getLineCommentPrefix(@NotNull PsiFile file) {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.lineCommentPrefix : super.getLineCommentPrefix(file);
    }

    @Override
    @Nullable
    public String getBlockCommentPrefix(@NotNull PsiFile file) {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.blockCommentPrefix : super.getBlockCommentPrefix(file);
    }

    @Override
    @Nullable
    public String getBlockCommentSuffix(@NotNull PsiFile file) {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.blockCommentSuffix : super.getBlockCommentSuffix(file);
    }

    @Override
    @NotNull
    public String getStatementTerminatorCharacters(@NotNull PsiFile file) {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return (clientConfiguration != null) ? clientConfiguration.statementTerminatorCharacters : super.getStatementTerminatorCharacters(file);
    }
}

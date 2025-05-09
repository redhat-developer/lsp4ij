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

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Adds client-side configuration features.
 */
public class UserDefinedClientFeatures extends LSPClientFeatures {

    public UserDefinedClientFeatures() {
        super();

        // Use the extended feature implementations
        setCompletionFeature(new UserDefinedCompletionFeature());
        setFormattingFeature(new UserDefinedFormattingFeature());
        setReferencesFeature(new UserDefinedReferencesFeature());
        setWorkspaceSymbolFeature(new UserDefinedWorkspaceSymbolFeature());
        setBreadcrumbsFeature(new UserDefinedBreadcrumbsFeature());
        setEditorBehaviorFeature(new UserDefinedEditorBehaviorFeature(this));
    }

    @Override
    public @Nullable URI getFileUri(@NotNull VirtualFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        if (clientConfiguration != null && clientConfiguration.uriSupport.encoded) {
            return FileUriSupport.ENCODED.getFileUri(file);
        }
        return FileUriSupport.DEFAULT.getFileUri(file);
    }

    @Override
    public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        if (clientConfiguration != null && clientConfiguration.uriSupport.encoded) {
            return FileUriSupport.ENCODED.findFileByUri(fileUri);
        }
        return FileUriSupport.DEFAULT.findFileByUri(fileUri);
    }

    @Override
    public String toString(@NotNull VirtualFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        if (clientConfiguration != null && clientConfiguration.uriSupport.encoded) {
            return FileUriSupport.ENCODED.toString(file);
        }
        return FileUriSupport.DEFAULT.toString(file);
    }

    @Override
    public @Nullable String toString(@NotNull URI fileUri, boolean directory) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        if (clientConfiguration != null && clientConfiguration.uriSupport.encoded) {
            return FileUriSupport.ENCODED.toString(fileUri, directory);
        }
        return FileUriSupport.DEFAULT.toString(fileUri, directory);
    }

    public boolean isCaseSensitive(@NotNull PsiFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        return clientConfiguration != null ? clientConfiguration.caseSensitive : super.isCaseSensitive(file);
    }

    @Override
    @Nullable
    public String getLineCommentPrefix(@NotNull PsiFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        return clientConfiguration != null ? clientConfiguration.lineCommentPrefix : super.getLineCommentPrefix(file);
    }

    @Override
    @Nullable
    public String getBlockCommentPrefix(@NotNull PsiFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        return clientConfiguration != null ? clientConfiguration.blockCommentPrefix : super.getBlockCommentPrefix(file);
    }

    @Override
    @Nullable
    public String getBlockCommentSuffix(@NotNull PsiFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        return clientConfiguration != null ? clientConfiguration.blockCommentSuffix : super.getBlockCommentSuffix(file);
    }

    @Override
    @NotNull
    public String getStatementTerminatorCharacters(@NotNull PsiFile file) {
        ClientConfigurationSettings clientConfiguration = getClientConfigurationSettings();
        return clientConfiguration != null ? clientConfiguration.statementTerminatorCharacters : super.getStatementTerminatorCharacters(file);
    }

    private @Nullable ClientConfigurationSettings getClientConfigurationSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getServerDefinition();
        return serverDefinition.getLanguageServerClientConfiguration();
    }

}

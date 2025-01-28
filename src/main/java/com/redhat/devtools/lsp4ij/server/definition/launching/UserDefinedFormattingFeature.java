/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientSideOnTypeFormattingSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ServerSideOnTypeFormattingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds client-side formatting configuration features.
 */
public class UserDefinedFormattingFeature extends LSPFormattingFeature {

    // Server-side on-type formatting

    @Nullable
    private ServerSideOnTypeFormattingSettings getServerSideOnTypeFormattingSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.format.onTypeFormatting.serverSide : null;
    }

    @Override
    public boolean isOnTypeFormattingEnabled(@NotNull PsiFile file) {
        ServerSideOnTypeFormattingSettings settings = getServerSideOnTypeFormattingSettings();
        return settings != null ? settings.enabled : super.isOnTypeFormattingEnabled(file);
    }

    // Client-side on-type formatting

    @Nullable
    private ClientSideOnTypeFormattingSettings getClientSideOnTypeFormattingSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.format.onTypeFormatting.clientSide : null;
    }

    @Override
    public boolean isFormatOnCloseBrace(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnCloseBrace : super.isFormatOnCloseBrace(file);
    }

    @Override
    @Nullable
    public String getFormatOnCloseBraceCharacters(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnCloseBraceCharacters : super.getFormatOnCloseBraceCharacters(file);
    }

    @Override
    @NotNull
    public FormattingScope getFormatOnCloseBraceScope(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings formatSettings = getClientSideOnTypeFormattingSettings();
        return formatSettings != null ? formatSettings.formatOnCloseBraceScope : super.getFormatOnCloseBraceScope(file);
    }

    @Override
    public boolean isFormatOnStatementTerminator(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnStatementTerminator : super.isFormatOnStatementTerminator(file);
    }

    @Override
    @Nullable
    public String getFormatOnStatementTerminatorCharacters(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnStatementTerminatorCharacters : super.getFormatOnStatementTerminatorCharacters(file);
    }

    @Override
    @NotNull
    public FormattingScope getFormatOnStatementTerminatorScope(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnStatementTerminatorScope : super.getFormatOnStatementTerminatorScope(file);
    }

    @Override
    public boolean isFormatOnCompletionTrigger(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnCompletionTrigger : super.isFormatOnCompletionTrigger(file);
    }

    @Override
    @Nullable
    public String getFormatOnCompletionTriggerCharacters(@NotNull PsiFile file) {
        ClientSideOnTypeFormattingSettings settings = getClientSideOnTypeFormattingSettings();
        return settings != null ? settings.formatOnCompletionTriggerCharacters : super.getFormatOnCompletionTriggerCharacters(file);
    }
}
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
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientConfigurationFormatSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings.ClientConfigurationOnTypeFormattingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds client-side formatting configuration features.
 */
public class UserDefinedFormattingFeature extends LSPFormattingFeature {

    @Nullable
    private ClientConfigurationOnTypeFormattingSettings getOnTypeFormattingSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.onTypeFormatting : null;
    }

    @Override
    public boolean isOnTypeFormattingEnabled(@NotNull PsiFile file) {
        ClientConfigurationOnTypeFormattingSettings onTypeFormattingSettings = getOnTypeFormattingSettings();
        return onTypeFormattingSettings != null ? onTypeFormattingSettings.enabled : super.isOnTypeFormattingEnabled(file);
    }

    @Nullable
    private ClientConfigurationFormatSettings getFormatSettings() {
        ClientConfigurableLanguageServerDefinition serverDefinition = (ClientConfigurableLanguageServerDefinition) getClientFeatures().getServerDefinition();
        ClientConfigurationSettings clientConfiguration = serverDefinition.getLanguageServerClientConfiguration();
        return clientConfiguration != null ? clientConfiguration.format : null;
    }

    @Override
    public boolean isFormatOnCloseBrace(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnCloseBrace : super.isFormatOnCloseBrace(file);
    }

    @Override
    @Nullable
    public String getFormatOnCloseBraceCharacters(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnCloseBraceCharacters : super.getFormatOnCloseBraceCharacters(file);
    }

    @Override
    @NotNull
    public FormattingScope getFormatOnCloseBraceScope(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnCloseBraceScope : super.getFormatOnCloseBraceScope(file);
    }

    @Override
    public boolean isFormatOnStatementTerminator(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnStatementTerminator : super.isFormatOnStatementTerminator(file);
    }

    @Override
    @Nullable
    public String getFormatOnStatementTerminatorCharacters(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnStatementTerminatorCharacters : super.getFormatOnStatementTerminatorCharacters(file);
    }

    @Override
    @NotNull
    public FormattingScope getFormatOnStatementTerminatorScope(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnStatementTerminatorScope : super.getFormatOnStatementTerminatorScope(file);
    }

    @Override
    public boolean isFormatOnCompletionTrigger(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnCompletionTrigger : super.isFormatOnCompletionTrigger(file);
    }

    @Override
    @Nullable
    public String getFormatOnCompletionTriggerCharacters(@NotNull PsiFile file) {
        ClientConfigurationFormatSettings formatSettings = getFormatSettings();
        return formatSettings != null ? formatSettings.formatOnCompletionTriggerCharacters : super.getFormatOnCompletionTriggerCharacters(file);
    }
}
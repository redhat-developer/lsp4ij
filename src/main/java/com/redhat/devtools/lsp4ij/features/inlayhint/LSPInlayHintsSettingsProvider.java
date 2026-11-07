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
package com.redhat.devtools.lsp4ij.features.inlayhint;

import com.intellij.codeInsight.hints.ImmediateConfigurable;
import com.intellij.codeInsight.hints.InlayGroup;
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings;
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel;
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link InlaySettingsProvider} implementation that exposes the LSP declarative
 * inlay-hint provider in <em>Settings &gt; Editor &gt; Inlay Hints</em>,
 * so users can re-enable it after disabling via the editor context menu.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1800">Issue #1800</a>
 */
public class LSPInlayHintsSettingsProvider implements InlaySettingsProvider {

    @Override
    public @NotNull Collection<Language> getSupportedLanguages(@NotNull Project project) {
        return LanguageServersRegistry.getInstance()
                .getDeclarativeInlayHintProviderInfos()
                .keySet()
                .stream()
                .map(Language::findLanguageByID)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public @NotNull List<InlayProviderSettingsModel> createModels(@NotNull Project project,
                                                                  @NotNull Language language) {
        if (!LanguageServersRegistry.getInstance()
                .getDeclarativeInlayHintProviderInfos()
                .containsKey(language.getID())) {
            return Collections.emptyList();
        }

        String providerId = LSPInlayHintsProvider.getProviderId(language.getID());
        Boolean enabled = DeclarativeInlayHintsSettings.Companion.getInstance()
                .isProviderEnabled(providerId);
        boolean isEnabled = enabled == null || enabled;

        return List.of(new LSPInlayHintsSettingsModel(isEnabled, providerId, language));
    }

    private static class LSPInlayHintsSettingsModel extends InlayProviderSettingsModel {

        private boolean initialEnabled;

        LSPInlayHintsSettingsModel(boolean isEnabled, @NotNull String providerId, @NotNull Language language) {
            super(isEnabled, providerId, language);
            this.initialEnabled = isEnabled;
        }

        @Override
        public @NotNull String getName() {
            return LanguageServerBundle.message("lsp.hints.declarative.provider.name");
        }

        @Override
        public @NotNull InlayGroup getGroup() {
            return InlayGroup.OTHER_GROUP;
        }

        @Override
        public @NotNull JComponent getComponent() {
            return new JPanel();
        }

        @Override
        public @Nullable String getDescription() {
            return null;
        }

        @Override
        public @Nullable String getPreviewText() {
            return null;
        }

        @Override
        public @Nullable String getCasePreview(@NotNull ImmediateConfigurable.Case aCase) {
            return null;
        }

        @Override
        public @Nullable Language getCasePreviewLanguage(@NotNull ImmediateConfigurable.Case aCase) {
            return null;
        }

        @Override
        public @Nullable String getCaseDescription(@NotNull ImmediateConfigurable.Case aCase) {
            return null;
        }

        @Override
        public void apply() {
            DeclarativeInlayHintsSettings.Companion.getInstance()
                    .setProviderEnabled(getId(), isEnabled());
            initialEnabled = isEnabled();
        }

        @Override
        public boolean isModified() {
            return isEnabled() != initialEnabled;
        }

        @Override
        public void reset() {
            Boolean enabled = DeclarativeInlayHintsSettings.Companion.getInstance()
                    .isProviderEnabled(getId());
            initialEnabled = enabled == null || enabled;
            setEnabled(initialEnabled);
        }

        @Override
        public @NotNull String getMainCheckBoxLabel() {
            return getName();
        }

        @Override
        public @NotNull List<ImmediateConfigurable.Case> getCases() {
            return Collections.emptyList();
        }
    }
}

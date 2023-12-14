/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.ColumnInfo;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UI Table of language mappings to Insert/Delete language mappings.
 */
public class LanguageServerMappingTablePanel extends ServerMappingTablePanel {

    public LanguageServerMappingTablePanel() {
        this(true);
    }

    public LanguageServerMappingTablePanel(boolean editable) {
        super(new LanguageColumn(editable), editable);
        getTable().getEmptyText().setText(LanguageServerBundle.message("new.language.server.dialog.mappings.language.no"));
    }

    @Override
    protected ServerMappingSettings createServerMappingSettings() {
        Collection<String> availableLanguages = getAvailableLanguages();
        return ServerMappingSettings.createLanguageMappingSettings(availableLanguages.stream().findFirst().get(), null);
    }

    @NotNull
    private static Collection<String> getAvailableLanguages() {
        Collection<String> languages = Language.getRegisteredLanguages()
                .stream()
                .map(Language::getID)
                .collect(Collectors.toList())
                .stream().sorted()
                .collect(Collectors.toList());
        //val configuredFileTypes = models.tableModel.items.map { it.fileTypeName }.toSet()
        Collection<String> availableLanguages = languages; // - configuredFileTypes
        return availableLanguages;
    }

    private static class LanguageColumn extends ColumnInfo<ServerMappingSettings, String> {

        private final boolean editable;

        public LanguageColumn(boolean editable) {
            super(LanguageServerBundle.message("new.language.server.dialog.mappings.language.column"));
            this.editable = editable;
        }

        @Override
        public @Nullable String valueOf(ServerMappingSettings mapping) {
            return mapping.getLanguage();
        }

        @Override
        public void setValue(ServerMappingSettings mapping, String value) {
            mapping.setLanguage(value);
        }

        @Override
        public @Nullable TableCellEditor getEditor(ServerMappingSettings serverMappingSettings) {
            return createComboBoxRendererAndEditor();
        }

        @Override
        public @Nullable TableCellRenderer getRenderer(ServerMappingSettings serverMappingSettings) {
            return createComboBoxRendererAndEditor();
        }

        @Override
        public boolean isCellEditable(ServerMappingSettings serverMappingSettings) {
            return editable;
        }

        private ComboBoxTableRenderer<String> createComboBoxRendererAndEditor() {
            Collection<String> availableLanguages = getAvailableLanguages();
            return new LanguageCellComboBox(availableLanguages).withClickCount(1);
        }

        private class LanguageCellComboBox extends ComboBoxTableRenderer<String> {

            private Map<String, Language> languages = Language.getRegisteredLanguages()
                    .stream()
                    .filter(language -> !language.equals(Language.ANY))
                    .collect(Collectors.toMap(Language::getID, Function.identity()));

            public LanguageCellComboBox(Collection<String> availableLanguages) {
                super(availableLanguages.stream().toArray(String[]::new));
            }

            @Override
            protected @NlsContexts.Label String getTextFor(@NotNull String value) {
                Language language = languages.get(value);
                return language != null ? language.getDisplayName() : value + " (Unknown)";
            }

            @Override
            protected Icon getIconFor(@NotNull String value) {
                Language language = languages.get(value);
                if (language != null) {
                    FileType fileType = LanguageUtil.getLanguageFileType(language);
                    return fileType != null ? fileType.getIcon() : null;
                }
                return null;
            }
        }
    }

}

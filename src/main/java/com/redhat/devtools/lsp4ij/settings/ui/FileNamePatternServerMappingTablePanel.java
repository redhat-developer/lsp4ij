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
package com.redhat.devtools.lsp4ij.settings.ui;

import com.intellij.util.ui.ColumnInfo;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * UI table used to insert/delete file name patterns mappings for a given language server definition.
 */
public class FileNamePatternServerMappingTablePanel extends AbstractServerMappingTablePanel {

    public FileNamePatternServerMappingTablePanel() {
        this(true);
    }

    public FileNamePatternServerMappingTablePanel(boolean editable) {
        super(new FileNamePatternColumn(editable), editable);
        getTable().getEmptyText().setText(LanguageServerBundle.message("new.language.server.dialog.mappings.fileNamePattern.no"));
    }

    @Override
    protected ServerMappingSettings createServerMappingSettings() {
        return ServerMappingSettings.createFileNamePatternsMappingSettings(new ArrayList<>(), null);
    }

    private static class FileNamePatternColumn extends ColumnInfo<ServerMappingSettings, String> {

            private final boolean editable;

            public FileNamePatternColumn(boolean editable) {
                super(LanguageServerBundle.message("new.language.server.dialog.mappings.fileNamePattern.column"));
                this.editable = editable;
            }

            @Override
            public @Nullable String valueOf(ServerMappingSettings mapping) {
                return mapping.getFileNamePatterns().stream().collect(Collectors.joining(";"));
            }

            @Override
            public void setValue(ServerMappingSettings mapping, String value) {
                mapping.setFileNamePatterns(Arrays.asList(value.split(";")));
            }

            @Override
            public boolean isCellEditable(ServerMappingSettings serverMappingSettings) {
                return editable;
            }
        }

}

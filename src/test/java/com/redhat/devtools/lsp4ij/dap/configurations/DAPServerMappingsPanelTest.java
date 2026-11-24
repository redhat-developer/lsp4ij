/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.settings.ui.ServerMappingsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The Mappings tab of a DAP server has no {@code Language Id} column, which only a language server uses (#1837), while
 * the one of a language server keeps it.
 */
public class DAPServerMappingsPanelTest extends BasePlatformTestCase {

    private static final String LANGUAGE = LanguageServerBundle.message("language.server.mappings.language.column");
    private static final String FILE_TYPE = LanguageServerBundle.message("language.server.mappings.fileType.column");
    private static final String FILE_NAME_PATTERNS = LanguageServerBundle.message("language.server.mappings.fileNamePattern.column");
    private static final String LANGUAGE_ID = LanguageServerBundle.message("language.server.mappings.languageId.column");

    public void testTheMappingsOfADapServerHaveNoLanguageIdColumn() {
        var builder = FormBuilder.createFormBuilder();
        new DAPServerMappingsPanel(builder, true);

        assertEquals(List.of(List.of(LANGUAGE), List.of(FILE_TYPE), List.of(FILE_NAME_PATTERNS)),
                columnsOfEachTable(builder.getPanel()));
    }

    public void testTheMappingsOfALanguageServerKeepTheLanguageIdColumn() {
        var builder = FormBuilder.createFormBuilder();
        new ServerMappingsPanel(builder, true);

        assertEquals(List.of(List.of(LANGUAGE, LANGUAGE_ID), List.of(FILE_TYPE, LANGUAGE_ID), List.of(FILE_NAME_PATTERNS, LANGUAGE_ID)),
                columnsOfEachTable(builder.getPanel()));
    }

    private static @NotNull List<List<String>> columnsOfEachTable(@NotNull JComponent panel) {
        return UIUtil.findComponentsOfType(panel, TableView.class)
                .stream()
                .map(table -> IntStream.range(0, table.getColumnCount())
                        .mapToObj(table::getColumnName)
                        .toList())
                .toList();
    }
}

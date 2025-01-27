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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.settings.ui.FileNamePatternServerMappingTablePanel;
import com.redhat.devtools.lsp4ij.settings.ui.FileTypeServerMappingTablePanel;
import com.redhat.devtools.lsp4ij.settings.ui.LanguageServerMappingTablePanel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * UI panel which display in a tabbed pane the mappings in three tabs:
 *
 * <ul>
 *     <li>a tab which show list of language mappings.</li>
 *     <li>a tab which show list of file type mappings.</li>
 *     <li>a tab which show list of file name pattern mappings.</li>
 * </ul>
 */
public class DAPServerMappingsPanel {

    private static final int LANGUAGE_TAB_INDEX = 0;
    private static final int FILE_TYPE_TAB_INDEX = 1;
    private static final int FILE_NAME_PATTERN_TAB_INDEX = 2;

    private LanguageServerMappingTablePanel languageMappingsPanel;
    private FileTypeServerMappingTablePanel fileTypeMappingsPanel;
    private FileNamePatternServerMappingTablePanel fileNamePatternMappingsPanel;
    private JBTabbedPane tabbedPane;

    public DAPServerMappingsPanel(FormBuilder builder, boolean editable) {
        createContent(builder, editable);
    }

    private void createContent(FormBuilder builder, boolean editable) {
        tabbedPane = new JBTabbedPane();
        builder.addLabeledComponent(DAPBundle.message("dap.settings.editor.mappings.title"), tabbedPane, true);

        // Language mappings
        createLanguageMappingsContent(tabbedPane, editable);
        // File type mappings
        createFileTypeMappingsContent(tabbedPane, editable);
        // File name pattern mappings
        createFileNamePatternMappingsContent(tabbedPane, editable);
    }

    private void createLanguageMappingsContent(JBTabbedPane tabbedPane, boolean editable) {
        languageMappingsPanel = new LanguageServerMappingTablePanel(editable);
        languageMappingsPanel.addChangeHandler(() -> updateTabTitleAt(LANGUAGE_TAB_INDEX));
        tabbedPane.add(DAPBundle.message("dap.settings.editor.mappings.language"), languageMappingsPanel);
    }

    private void createFileTypeMappingsContent(JBTabbedPane tabbedPane, boolean editable) {
        fileTypeMappingsPanel = new FileTypeServerMappingTablePanel(editable);
        fileTypeMappingsPanel.addChangeHandler(() -> updateTabTitleAt(FILE_TYPE_TAB_INDEX));
        tabbedPane.add(DAPBundle.message("dap.settings.editor.mappings.fileType"), fileTypeMappingsPanel);
    }

    private void createFileNamePatternMappingsContent(JBTabbedPane tabbedPane, boolean editable) {
        fileNamePatternMappingsPanel = new FileNamePatternServerMappingTablePanel(editable);
        fileNamePatternMappingsPanel.addChangeHandler(() -> updateTabTitleAt(FILE_NAME_PATTERN_TAB_INDEX));
        tabbedPane.add(DAPBundle.message("dap.settings.editor.mappings.fileNamePattern"), fileNamePatternMappingsPanel);
    }

    /**
     * Refresh the language, file type, file name pattern mappings defined by the DAP factory.
     *
     * @param languageMappings language mappings.
     * @param allFileTypeMappings file type and pattern mappings.
     */
    public void refreshMappings(@NotNull List<ServerMappingSettings> languageMappings,
                                @NotNull List<ServerMappingSettings> allFileTypeMappings) {
        // refresh language mappings list
        setLanguageMappings(languageMappings);

        // refresh file type mappings
        List<ServerMappingSettings> fileTypeMappings = new ArrayList<>();
        List<ServerMappingSettings> fileNamePatternMappings = new ArrayList<>();
        // File type and file name patterns are defined in the same "fileType" object.
        // {
        //  "fileType": {
        //    "name": "LESS",
        //    "patterns": [
        //      "*.less"
        //    ]
        //  },
        //  "languageId": "less"
        //}

        // Collect them by following this strategy:
        // - case 1: if fileType can be retrieved by the name, create a fileType mapping.
        // - case 2: if fileType cannot be retrieved, create file name pattern mapping.
        for (var mapping : allFileTypeMappings) {
            boolean add = false;
            String fileType = mapping.getFileType();
            if (!StringUtils.isEmpty(fileType)) {
                if (FileTypeManager.getInstance().findFileTypeByName(fileType) != null) {
                    // The fileType exists, create a file type mapping
                    fileTypeMappings.add(mapping);
                    add = true;
                }
            }
            if (!add) {
                // The fileType doesn't exist, create a file name pattern mapping
                List<String> patterns = mapping.getFileNamePatterns();
                if (patterns != null) {
                    fileNamePatternMappings.add(mapping);
                }
            }
        }

        // refresh file type mappings list
        setFileTypeMappings(fileTypeMappings);
        // refresh file name pattern mappings
        setFileNamePatternMappings(fileNamePatternMappings);
    }

    public List<ServerMappingSettings> getLanguageMappings() {
        return languageMappingsPanel.getServerMappings();
    }

    public List<ServerMappingSettings> getFileTypeMappings() {
        return fileTypeMappingsPanel.getServerMappings();
    }

    public List<ServerMappingSettings> getFileNamePatternMappings() {
        return fileNamePatternMappingsPanel.getServerMappings();
    }

    public void setLanguageMappings(List<ServerMappingSettings> mappings) {
        languageMappingsPanel.refresh(mappings);
        updateSelectedTab();
    }

    public void setFileTypeMappings(List<ServerMappingSettings> mappings) {
        fileTypeMappingsPanel.refresh(mappings);
        updateSelectedTab();
    }

    public void setFileNamePatternMappings(List<ServerMappingSettings> mappings) {
        fileNamePatternMappingsPanel.refresh(mappings);
        updateSelectedTab();
    }

    /**
     * Select and show the mapping tab (language, file type, file name pattern) which defines the most mappings.
     */
    private void updateSelectedTab() {
        int tabIndex = 0;
        Map<Integer, Integer> mappingsSize = new HashMap<>();
        mappingsSize.put(fileNamePatternMappingsPanel.getServerMappings().size(), FILE_NAME_PATTERN_TAB_INDEX);
        mappingsSize.put(fileTypeMappingsPanel.getServerMappings().size(), FILE_TYPE_TAB_INDEX);
        mappingsSize.put(languageMappingsPanel.getServerMappings().size(), LANGUAGE_TAB_INDEX);

        Optional<Integer> max = mappingsSize
                .keySet()
                .stream()
                .max(Integer::compare);
        if (!max.isEmpty()) {
            tabIndex = mappingsSize.get(max.get());
        }
        tabbedPane.setSelectedIndex(tabIndex);
    }

    /**
     * Update the tab title from the given index with the number of mappings (ex : "Language (3)").
     *
     * @param tabIndex the tab index.
     */
    private void updateTabTitleAt(int tabIndex) {
        String baseTabTitle = null;
        List<ServerMappingSettings> mappings = null;
        switch (tabIndex) {
            case LANGUAGE_TAB_INDEX:
                baseTabTitle = DAPBundle.message("dap.settings.editor.mappings.language");
                mappings = languageMappingsPanel.getServerMappings();
                break;
            case FILE_TYPE_TAB_INDEX:
                baseTabTitle = DAPBundle.message("dap.settings.editor.mappings.fileType");
                mappings = fileTypeMappingsPanel.getServerMappings();
                break;
            case FILE_NAME_PATTERN_TAB_INDEX:
                baseTabTitle = DAPBundle.message("dap.settings.editor.mappings.fileNamePattern");
                mappings = fileNamePatternMappingsPanel.getServerMappings();
                break;
        }
        String tabTitle = mappings.isEmpty() ? baseTabTitle : baseTabTitle + " (" + mappings.size() + ")";
        tabbedPane.setTitleAt(tabIndex, tabTitle);
    }

    /**
     * Returns a list of language, file type, file name pattern mappings.
     *
     * @return a list of language, file type, file name pattern mappings.
     */
    public @NotNull List<ServerMappingSettings> getAllMappings() {
        List<ServerMappingSettings> mappingSettings = new ArrayList<>(getLanguageMappings());
        mappingSettings.addAll(getFileTypeMappings());
        mappingSettings.addAll(getFileNamePatternMappings());
        return mappingSettings;
    }

}

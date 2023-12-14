package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
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
import java.util.stream.Stream;

public class FileTypeServerMappingTablePanel extends ServerMappingTablePanel {

    public FileTypeServerMappingTablePanel() {
        this(true);
    }

    public FileTypeServerMappingTablePanel(boolean editable) {
        super(new FileTypeColumn(editable), editable);
        getTable().getEmptyText().setText(LanguageServerBundle.message("new.language.server.dialog.mappings.fileType.no"));
    }

    @Override
    protected ServerMappingSettings createServerMappingSettings() {
        Collection<String> availableFileTypes = getAvailableFileTypes();
        return ServerMappingSettings.createFileTypeMappingSettings(availableFileTypes.stream().findFirst().get(), null);
    }

    private static Collection<String> getAvailableFileTypes() {
        Collection<String> fileTypes = Stream.of(FileTypeManager.getInstance().getRegisteredFileTypes())
                .map(FileType::getName)
                .collect(Collectors.toList())
                .stream().sorted()
                .collect(Collectors.toList());
        //val configuredFileTypes = models.tableModel.items.map { it.fileTypeName }.toSet()
        Collection<String> availableFileTypes = fileTypes; // - configuredFileTypes
        return availableFileTypes;
    }

    private static class FileTypeColumn extends ColumnInfo<ServerMappingSettings, String> {

        private final boolean editable;

        public FileTypeColumn(boolean editable) {
            super(LanguageServerBundle.message("new.language.server.dialog.mappings.fileType.column"));
            this.editable = editable;
        }

        @Override
        public @Nullable String valueOf(ServerMappingSettings mapping) {
            return mapping.getFileType();
        }

        @Override
        public void setValue(ServerMappingSettings mapping, String value) {
            mapping.setFileType(value);
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
            Collection<String> availableFileTypes = getAvailableFileTypes();
            return new FileTypeCellComboBox(availableFileTypes).withClickCount(1);
        }

        private class FileTypeCellComboBox extends ComboBoxTableRenderer<String> {

            private Map<String, FileType> fileTypes = Stream.of(FileTypeManager.getInstance().getRegisteredFileTypes())
                    .collect(Collectors.toMap(FileType::getName, Function.identity()));

            public FileTypeCellComboBox(Collection<String> availableFileTypes) {
                super(availableFileTypes.stream().toArray(String[]::new));
            }

            @Override
            protected @NlsContexts.Label String getTextFor(@NotNull String value) {
                FileType fileType = fileTypes.get(value);
                return fileType != null ? fileType.getDisplayName() : value + " (Unknown)";
            }

            @Override
            protected Icon getIconFor(@NotNull String value) {
                FileType fileType = fileTypes.get(value);
                return fileType != null ? fileType.getIcon() : null;
            }

        }
    }

}

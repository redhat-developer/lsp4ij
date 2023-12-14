package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ServerMappingTablePanel extends BorderLayoutPanel {

    private final TableView<ServerMappingSettings> table;

    public ServerMappingTablePanel(ColumnInfo<ServerMappingSettings, String> columnId, boolean editable) {
        ListTableModel<ServerMappingSettings> model = new ListTableModel<>(
                new ColumnInfo[]{columnId, new LanguageIdColumn(editable)},
                new ArrayList<>(),
                0);
        table = new TableView<>(model);

        ToolbarDecorator toolbar = ToolbarDecorator.createDecorator(table)
                .setAddAction(addData())
                .setRemoveAction(removeData())
                .disableUpDownActions();
        if (!editable) {
            toolbar.disableAddAction();
            toolbar.disableRemoveAction();
        }
        add(toolbar.createPanel());
    }

    public List<ServerMappingSettings> getServerMappings() {
        return table.getListTableModel().getItems();
    }

    private AnActionButtonRunnable addData() {
        return new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                ServerMappingSettings newMapping = createServerMappingSettings();
                table.getListTableModel().addRow(newMapping);
            }
        };
    }

    protected abstract ServerMappingSettings createServerMappingSettings();

    private AnActionButtonRunnable removeData() {
        return new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                table.getListTableModel().removeRow(table.getSelectedRow());
            }
        };
    }

    public void refresh(@NotNull List<ServerMappingSettings> mappings) {
        table.getListTableModel().setItems(mappings);
    }


    private static class LanguageIdColumn extends ColumnInfo<ServerMappingSettings, String> {

        private final boolean editable;

        public LanguageIdColumn(boolean editable) {
            super(LanguageServerBundle.message("new.language.server.dialog.mappings.languageId.column"));
            this.editable = editable;
        }

        @Override
        public @Nullable String valueOf(ServerMappingSettings mapping) {
            return mapping.getLanguageId();
        }

        @Override
        public void setValue(ServerMappingSettings mapping, String value) {
            mapping.setLanguageId(value);
        }

        @Override
        public boolean isCellEditable(ServerMappingSettings serverMappingSettings) {
            return editable;
        }
    }

    protected TableView<ServerMappingSettings> getTable() {
        return table;
    }
}

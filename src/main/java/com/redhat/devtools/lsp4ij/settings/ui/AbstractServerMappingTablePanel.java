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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for filling language server mappings with a UI table.
 */
public abstract class AbstractServerMappingTablePanel extends BorderLayoutPanel {

    private final TableView<ServerMappingSettings> table;

    private final List<Runnable> myChangeHandlers = ContainerUtil.createConcurrentList();

    public AbstractServerMappingTablePanel(ColumnInfo<ServerMappingSettings, String> columnId, boolean editable) {
        ListTableModel<ServerMappingSettings> model = new ListTableModel<>(
                new ColumnInfo[]{columnId, new LanguageIdColumn(editable)},
                new ArrayList<>(),
                0);
        table = new TableView<>(model);
        table.setColumnSelectionAllowed(true);

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
        return anActionButton -> {
            ServerMappingSettings newMapping = createServerMappingSettings();
            table.getListTableModel().addRow(newMapping);
            fireStateChanged();
            // Immediately select the first cell in the new row
            ApplicationManager.getApplication().invokeLater(() -> {
                int newRowIndex = table.getRowCount() - 1;
                table.getSelectionModel().setSelectionInterval(newRowIndex, newRowIndex);
                table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);
                table.requestFocusInWindow();
            });
        };
    }

    protected abstract ServerMappingSettings createServerMappingSettings();

    private AnActionButtonRunnable removeData() {
        return anActionButton -> {
            table.getListTableModel().removeRow(table.getSelectedRow());
            fireStateChanged();
        };
    }

    public void refresh(@NotNull List<ServerMappingSettings> mappings) {
        table.getListTableModel().setItems(mappings);
        fireStateChanged();
    }

    private static class LanguageIdColumn extends ColumnInfo<ServerMappingSettings, String> {

        private final boolean editable;

        public LanguageIdColumn(boolean editable) {
            super(LanguageServerBundle.message("language.server.mappings.languageId.column"));
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

    /**
     * Adds the given changeHandler to the list of registered change handlers
     *
     * @param changeHandler the changeHandler to remove
     */
    public void addChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.add(changeHandler);
    }

    /**
     * Removes the given changeHandler from the list of registered change handlers
     *
     * @param changeHandler the changeHandler to remove
     */
    public void removeChangeHandler(@NotNull Runnable changeHandler) {
        myChangeHandlers.remove(changeHandler);
    }

    /**
     * Notifies all registered change handlers when the state changed
     */
    private void fireStateChanged() {
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }
}

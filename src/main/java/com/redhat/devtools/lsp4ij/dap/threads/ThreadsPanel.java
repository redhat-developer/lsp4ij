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
package com.redhat.devtools.lsp4ij.dap.threads;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.*;

/**
 * Threads panel which shows DAP threads (name and status).
 */
public class ThreadsPanel extends BorderLayoutPanel implements Disposable {

    public static final @NotNull String ID = "dap-threads-panel";

    private final ListTableModel<ThreadInfo> model;
    private final TableView<ThreadInfo> table;
    private final DAPDebugProcess debugProcess;

    public ThreadsPanel(@NotNull DAPDebugProcess debugProcess) {
        this.debugProcess = debugProcess;

        this.model = new ListTableModel<>(new NameColumnInfo());
        this.table = new TableView<>(model);

        this.addToCenter(ScrollPaneFactory.createScrollPane(table));
    }

    public JComponent getDefaultFocusedComponent() {
        return table;
    }

    @Override
    public void dispose() {}

    public void refreshThreads(Thread[] threads) {
        Map<Integer, ThreadInfo> existingById = new HashMap<>();
        for (ThreadInfo info : model.getItems()) {
            existingById.put(info.getId(), info);
        }

        List<ThreadInfo> updated = new ArrayList<>();
        for (Thread thread : threads) {
            ThreadInfo info = existingById.getOrDefault(thread.getId(), new ThreadInfo(thread.getId(), thread.getName()));
            updated.add(info);
        }

        ApplicationManager.getApplication().invokeLater(() -> model.setItems(updated));
    }

    public void refreshThread(ThreadEventArguments args) {
        refreshThread(args, true);
    }

    private void refreshThread(ThreadEventArguments args, boolean reloadIfMissing) {
        for (ThreadInfo info : model.getItems()) {
            if (info.getId() == args.getThreadId()) {
                info.setStatus(args.getReason());
                ApplicationManager.getApplication().invokeLater(model::fireTableDataChanged);
                return;
            }
        }

        if (reloadIfMissing) {
            debugProcess.getThreads().thenAccept(this::refreshThreads);
        }
    }

    private static class ThreadInfo {
        private final int id;
        private final String name;
        private String status;

        public ThreadInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() { return id; }

        public String getName() { return name; }

        public @Nullable String getStatus() { return status; }

        public void setStatus(@Nullable String status) { this.status = status; }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ThreadInfo that)) return false;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    private static class NameColumnInfo extends ColumnInfo<ThreadInfo, ThreadInfo> {
        public NameColumnInfo() {
            super("Thread");
        }

        @Override
        public ThreadInfo valueOf(ThreadInfo info) {
            return info;
        }

        @Override
        public TableCellRenderer getRenderer(ThreadInfo info) {
            return new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
                    if (value instanceof ThreadInfo thread) {
                        append(thread.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        if (thread.getStatus() != null) {
                            append(" [" + thread.getStatus() + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                        }
                        setIcon(getIconForStatus(thread.getStatus()));
                    }
                }
            };
        }

        private Icon getIconForStatus(@Nullable String status) {
            if (ThreadEventArgumentsReason.STARTED.equalsIgnoreCase(status)) {
                return AllIcons.Debugger.ThreadRunning;
            } else {
                return AllIcons.Debugger.ThreadSuspended;
            }
        }

    }
}

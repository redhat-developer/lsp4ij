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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunState;
import com.redhat.devtools.lsp4ij.dap.client.DAPThreadRunStates;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.util.*;

/**
 * Threads panel which shows DAP threads (name, and whether the thread runs, is stopped or has exited), with a context
 * menu of actions on the selected threads.
 */
public class ThreadsPanel extends BorderLayoutPanel implements Disposable, UiDataProvider {

    public static final @NotNull String ID = "dap-threads-panel";

    /**
     * The group of the actions of the panel's context menu.
     */
    public static final @NotNull String POPUP_GROUP_ID = "DAP.Threads.Popup";

    /**
     * The threads selected in the panel, for the actions of its context menu.
     */
    public static final @NotNull DataKey<SelectedThreads> SELECTED_THREADS = DataKey.create("lsp4ij.dap.selectedThreads");

    /**
     * The threads selected in the panel, and the client of the DAP session they are threads of.
     *
     * @param client    the client of the DAP session of the threads.
     * @param threadIds the IDs of the selected threads, as that session numbers them.
     */
    public record SelectedThreads(@NotNull DAPClient client, @NotNull List<Integer> threadIds) {
    }

    // No icon says that a thread exited: the icon of a running thread, disabled
    private static final @NotNull Icon EXITED_ICON = IconLoader.getDisabledIcon(AllIcons.Debugger.ThreadRunning);

    private final ListTableModel<ThreadInfo> model;
    private final TableView<ThreadInfo> table;
    private final DAPDebugProcess debugProcess;
    // The client of the DAP session whose threads the panel shows, set on the EDT with them
    private volatile @Nullable DAPClient client;

    public ThreadsPanel(@NotNull DAPDebugProcess debugProcess) {
        this.debugProcess = debugProcess;

        this.model = new ListTableModel<>(new NameColumnInfo());
        this.table = new TableView<>(model);
        if (ActionManager.getInstance().getAction(POPUP_GROUP_ID) instanceof ActionGroup popupGroup) {
            PopupHandler.installPopupMenu(table, popupGroup, ActionPlaces.getPopupPlace(ID));
        }

        this.addToCenter(ScrollPaneFactory.createScrollPane(table));
    }

    public JComponent getDefaultFocusedComponent() {
        return table;
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
        var threadsClient = client;
        if (threadsClient != null) {
            var threadIds = table.getSelectedObjects().stream().map(ThreadInfo::getId).toList();
            sink.set(SELECTED_THREADS, new SelectedThreads(threadsClient, threadIds));
        }
    }

    @Override
    public void dispose() {}

    /**
     * Shows the given threads of the root session.
     *
     * @param threads the threads of the root session.
     */
    public void refreshThreads(Thread[] threads) {
        debugProcess.refreshThreads(threads);
    }

    /**
     * Shows the given threads, as threads of the given client's DAP session, with their run states in that session:
     * the sessions of a tree ('startDebugging') number their threads each. The rows are built on the EDT, with the run
     * states which the client has then.
     *
     * @param client  the client of the DAP session of the threads, or null if it is not known yet.
     * @param threads the threads.
     */
    public void refreshThreads(@Nullable DAPClient client, Thread @Nullable [] threads) {
        List<Thread> newThreads = threads != null ? List.of(threads) : List.of();
        ApplicationManager.getApplication().invokeLater(() -> {
            DAPThreadRunStates states = client != null ? client.getThreadRunStates() : DAPThreadRunStates.INITIAL;
            List<ThreadInfo> rows = new ArrayList<>();
            for (Thread thread : newThreads) {
                rows.add(new ThreadInfo(thread.getId(), thread.getName(), states.getState(thread.getId())));
            }
            this.client = client;
            model.setItems(rows);
        });
    }

    /**
     * Shows again whether each thread of the given client's DAP session runs, is stopped or has exited, as they
     * changed, when the panel shows the threads of that session: once the program stopped or continued, as the debug
     * adapter says, or once the session resumed it. The rows whose run state changed are updated on the EDT, with the
     * run states which the client has then, and keep their selection.
     *
     * @param client the client of the DAP session whose threads changed.
     */
    public void refreshThreadRunStates(@NotNull DAPClient client) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (client == this.client) {
                showThreadRunStates(client);
            }
        });
    }

    /**
     * Shows a 'thread' event of the root session.
     *
     * @param args the 'thread' event.
     */
    public void refreshThread(ThreadEventArguments args) {
        debugProcess.refreshThread(args);
    }

    /**
     * Shows a 'thread' event of the given client's DAP session, when the panel shows the threads of that session: the
     * run state of the thread, such as one which exited, or, for a thread it doesn't show, such as one which started,
     * the threads of that session.
     *
     * @param client the client of the DAP session which sent the event, or null if it is not known yet.
     * @param args   the 'thread' event.
     */
    public void refreshThread(@Nullable DAPClient client, ThreadEventArguments args) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (client == null || client != this.client) {
                // The panel shows the threads of another session, which numbers its threads itself
                return;
            }
            if (model.getItems().stream().anyMatch(info -> info.getId() == args.getThreadId())) {
                showThreadRunStates(client);
                return;
            }
            // Asked off the EDT, as LSP4J writes a request on the thread which sends it
            ApplicationManager.getApplication().executeOnPooledThread(() -> client.getThreads(true));
        });
    }

    /**
     * Updates, on the EDT, the rows whose run state differs from the one which the given client has now.
     */
    private void showThreadRunStates(@NotNull DAPClient client) {
        DAPThreadRunStates states = client.getThreadRunStates();
        List<ThreadInfo> rows = model.getItems();
        for (int row = 0; row < rows.size(); row++) {
            ThreadInfo info = rows.get(row);
            DAPThreadRunState state = states.getState(info.getId());
            if (state != info.getState()) {
                model.setItem(row, info.withState(state));
            }
        }
    }

    /**
     * Returns the icon of a thread with the given run state.
     *
     * @param state the run state of the thread.
     * @return the icon of a thread with the given run state.
     */
    static @NotNull Icon getRunStateIcon(@NotNull DAPThreadRunState state) {
        return switch (state) {
            case RUNNING -> AllIcons.Debugger.ThreadRunning;
            case STOPPED -> AllIcons.Debugger.ThreadSuspended;
            case EXITED -> EXITED_ICON;
        };
    }

    /**
     * Returns the text which a row shows for a thread with the given run state.
     *
     * @param state the run state of the thread.
     * @return the text which a row shows for a thread with the given run state.
     */
    static @NotNull @Nls String getRunStateText(@NotNull DAPThreadRunState state) {
        return switch (state) {
            case RUNNING -> DAPBundle.message("dap.threads.state.running");
            case STOPPED -> DAPBundle.message("dap.threads.state.stopped");
            case EXITED -> DAPBundle.message("dap.threads.state.exited");
        };
    }

    private static class ThreadInfo {
        private final int id;
        private final String name;
        private final @NotNull DAPThreadRunState state;

        public ThreadInfo(int id, String name, @NotNull DAPThreadRunState state) {
            this.id = id;
            this.name = name;
            this.state = state;
        }

        public int getId() { return id; }

        public String getName() { return name; }

        public @NotNull DAPThreadRunState getState() { return state; }

        public @NotNull ThreadInfo withState(@NotNull DAPThreadRunState state) { return new ThreadInfo(id, name, state); }

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
                        append(" [" + getRunStateText(thread.getState()) + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                        setIcon(getRunStateIcon(thread.getState()));
                    }
                }
            };
        }

    }
}

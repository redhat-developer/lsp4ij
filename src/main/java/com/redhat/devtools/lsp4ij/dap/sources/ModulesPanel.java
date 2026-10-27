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
package com.redhat.devtools.lsp4ij.dap.sources;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.ModuleEventArguments;
import org.eclipse.lsp4j.debug.ModuleEventArgumentsReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Modules panel, which shows the modules that the DAP server reports
 * (<a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_Modules">modules</a>
 * and the <a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_Module">module</a>
 * event): each one's name, version and path, and whether it is user code, in the order the DAP server gives them.
 * Each DAP session of the debug session, such as a child session started with 'startDebugging', keeps its own
 * modules, since a module id is valid only in its session.
 *
 * @param <S> the DAP session which reports the modules.
 */
public class ModulesPanel<S> extends BorderLayoutPanel implements Disposable {

    public static final @NotNull String ID = "dap-modules-panel";

    /**
     * A module, and the DAP session which reported it.
     *
     * @param session the DAP session which reported the module.
     * @param module  the module.
     * @param <S>     the DAP session.
     */
    public record SessionModule<S>(@NotNull S session, @NotNull Module module) {
    }

    private final ListTableModel<SessionModule<S>> model;
    private final TableView<SessionModule<S>> table;

    public ModulesPanel() {
        this.model = new ListTableModel<>(new NameColumnInfo(), new VersionColumnInfo(), new PathColumnInfo(), new UserCodeColumnInfo());
        this.table = new TableView<>(model);
        this.addToCenter(ScrollPaneFactory.createScrollPane(table));
    }

    public JComponent getDefaultFocusedComponent() {
        return table;
    }

    @Override
    public void dispose() {
    }

    /**
     * Shows the given modules of a DAP session in place of those it reported before, where the first of those was.
     * The modules of the other DAP sessions stay.
     *
     * @param session the DAP session.
     * @param modules the modules which the DAP session reports.
     */
    public void refresh(@NotNull S session, @NotNull Module[] modules) {
        List<Module> reported = List.of(modules);
        ApplicationManager.getApplication().invokeLater(() -> {
            List<SessionModule<S>> updated = new ArrayList<>();
            int at = -1;
            for (SessionModule<S> shown : model.getItems()) {
                if (shown.session() != session) {
                    updated.add(shown);
                } else if (at < 0) {
                    at = updated.size();
                }
            }
            updated.addAll(at >= 0 ? at : updated.size(),
                    reported.stream().map(module -> new SessionModule<>(session, module)).toList());
            model.setItems(updated);
        });
    }

    /**
     * Updates the shown modules of a DAP session with its 'module' event: a new module is added at the end, a changed
     * one replaces the one with its id, and a removed one is removed.
     *
     * @param session the DAP session.
     * @param args    the 'module' event.
     */
    public void update(@NotNull S session, @NotNull ModuleEventArguments args) {
        Module module = args.getModule();
        if (module == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            List<SessionModule<S>> updated = new ArrayList<>(model.getItems());
            int index = indexOf(updated, session, module);
            if (ModuleEventArgumentsReason.REMOVED.equals(args.getReason())) {
                if (index >= 0) {
                    updated.remove(index);
                }
            } else if (index >= 0) {
                updated.set(index, new SessionModule<>(session, module));
            } else {
                updated.add(new SessionModule<>(session, module));
            }
            model.setItems(updated);
        });
    }

    /**
     * @return the shown modules, in order.
     */
    public @NotNull List<Module> getModules() {
        return model.getItems().stream().map(SessionModule::module).toList();
    }

    private static <S> int indexOf(@NotNull List<SessionModule<S>> modules, @NotNull S session, @NotNull Module module) {
        for (int i = 0; i < modules.size(); i++) {
            SessionModule<S> shown = modules.get(i);
            if (shown.session() == session && Objects.equals(shown.module().getId(), module.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static class NameColumnInfo extends ColumnInfo<SessionModule<?>, String> {
        NameColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.name"));
        }

        @Override
        public @Nullable String valueOf(SessionModule<?> shown) {
            return shown.module().getName();
        }
    }

    private static class VersionColumnInfo extends ColumnInfo<SessionModule<?>, String> {
        VersionColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.version"));
        }

        @Override
        public @Nullable String valueOf(SessionModule<?> shown) {
            return shown.module().getVersion();
        }
    }

    private static class PathColumnInfo extends ColumnInfo<SessionModule<?>, String> {
        PathColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.path"));
        }

        @Override
        public @Nullable String valueOf(SessionModule<?> shown) {
            return shown.module().getPath();
        }
    }

    private static class UserCodeColumnInfo extends ColumnInfo<SessionModule<?>, String> {
        UserCodeColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.user.code"));
        }

        @Override
        public @Nullable String valueOf(SessionModule<?> shown) {
            Boolean userCode = shown.module().getIsUserCode();
            if (userCode == null) {
                return null;
            }
            return DAPBundle.message(userCode ? "dap.panels.modules.user.code.yes" : "dap.panels.modules.user.code.no");
        }
    }
}

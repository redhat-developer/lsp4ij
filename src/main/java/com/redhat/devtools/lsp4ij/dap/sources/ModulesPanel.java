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
 */
public class ModulesPanel extends BorderLayoutPanel implements Disposable {

    public static final @NotNull String ID = "dap-modules-panel";

    private final ListTableModel<Module> model;
    private final TableView<Module> table;

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
     * Shows the given modules in place of those shown.
     *
     * @param modules the modules which the DAP server reports.
     */
    public void refresh(@NotNull Module[] modules) {
        List<Module> shown = List.of(modules);
        ApplicationManager.getApplication().invokeLater(() -> model.setItems(new ArrayList<>(shown)));
    }

    /**
     * Updates the shown modules with a 'module' event: a new module is added at the end, a changed one replaces the
     * one with its id, and a removed one is removed.
     *
     * @param args the 'module' event.
     */
    public void update(@NotNull ModuleEventArguments args) {
        Module module = args.getModule();
        if (module == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            List<Module> updated = new ArrayList<>(model.getItems());
            int index = indexOf(updated, module);
            if (ModuleEventArgumentsReason.REMOVED.equals(args.getReason())) {
                if (index >= 0) {
                    updated.remove(index);
                }
            } else if (index >= 0) {
                updated.set(index, module);
            } else {
                updated.add(module);
            }
            model.setItems(updated);
        });
    }

    /**
     * @return the shown modules, in order.
     */
    public @NotNull List<Module> getModules() {
        return List.copyOf(model.getItems());
    }

    private static int indexOf(@NotNull List<Module> modules, @NotNull Module module) {
        for (int i = 0; i < modules.size(); i++) {
            if (Objects.equals(modules.get(i).getId(), module.getId())) {
                return i;
            }
        }
        return -1;
    }

    private static class NameColumnInfo extends ColumnInfo<Module, String> {
        NameColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.name"));
        }

        @Override
        public @Nullable String valueOf(Module module) {
            return module.getName();
        }
    }

    private static class VersionColumnInfo extends ColumnInfo<Module, String> {
        VersionColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.version"));
        }

        @Override
        public @Nullable String valueOf(Module module) {
            return module.getVersion();
        }
    }

    private static class PathColumnInfo extends ColumnInfo<Module, String> {
        PathColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.path"));
        }

        @Override
        public @Nullable String valueOf(Module module) {
            return module.getPath();
        }
    }

    private static class UserCodeColumnInfo extends ColumnInfo<Module, String> {
        UserCodeColumnInfo() {
            super(DAPBundle.message("dap.panels.modules.user.code"));
        }

        @Override
        public @Nullable String valueOf(Module module) {
            Boolean userCode = module.getIsUserCode();
            if (userCode == null) {
                return null;
            }
            return DAPBundle.message(userCode ? "dap.panels.modules.user.code.yes" : "dap.panels.modules.user.code.no");
        }
    }
}

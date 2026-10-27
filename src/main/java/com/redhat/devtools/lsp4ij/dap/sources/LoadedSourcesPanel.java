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
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import org.eclipse.lsp4j.debug.LoadedSourceEventArguments;
import org.eclipse.lsp4j.debug.LoadedSourceEventArgumentsReason;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loaded Sources panel, which shows the sources that the DAP server reports as loaded
 * (<a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_LoadedSources">loadedSources</a>
 * and the <a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_LoadedSource">loadedSource</a>
 * event): each one's name and path, in path order. Double-clicking a source, or Enter, opens it.
 */
public class LoadedSourcesPanel extends BorderLayoutPanel implements Disposable {

    public static final @NotNull String ID = "dap-loaded-sources-panel";

    private static final Comparator<Source> ORDER = Comparator.comparing(LoadedSourcesPanel::key);

    private final ListTableModel<Source> model;
    private final TableView<Source> table;

    /**
     * @param opener opens a source of the panel, by its path or its source reference.
     */
    public LoadedSourcesPanel(@NotNull Consumer<Source> opener) {
        this.model = new ListTableModel<>(new NameColumnInfo(), new PathColumnInfo());
        this.table = new TableView<>(model);
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(@NotNull MouseEvent event) {
                return open(opener);
            }
        }.installOn(table);
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER && open(opener)) {
                    event.consume();
                }
            }
        });
        this.addToCenter(ScrollPaneFactory.createScrollPane(table));
    }

    public JComponent getDefaultFocusedComponent() {
        return table;
    }

    @Override
    public void dispose() {
    }

    /**
     * Shows the given sources in place of those shown.
     *
     * @param sources the sources which the DAP server reports as loaded.
     */
    public void refresh(@NotNull Source[] sources) {
        List<Source> sorted = new ArrayList<>(List.of(sources));
        sorted.sort(ORDER);
        ApplicationManager.getApplication().invokeLater(() -> model.setItems(sorted));
    }

    /**
     * Updates the shown sources with a 'loadedSource' event: a new source is added, a changed one replaces the one with
     * its path or source reference, and a removed one is removed.
     *
     * @param args the 'loadedSource' event.
     */
    public void update(@NotNull LoadedSourceEventArguments args) {
        Source source = args.getSource();
        if (source == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            List<Source> updated = new ArrayList<>(model.getItems());
            updated.removeIf(shown -> sameSource(shown, source));
            if (!LoadedSourceEventArgumentsReason.REMOVED.equals(args.getReason())) {
                updated.add(source);
                updated.sort(ORDER);
            }
            model.setItems(updated);
        });
    }

    /**
     * @return the shown sources, in order.
     */
    public @NotNull List<Source> getSources() {
        return List.copyOf(model.getItems());
    }

    private boolean open(@NotNull Consumer<Source> opener) {
        Source selected = table.getSelectedObject();
        if (selected == null) {
            return false;
        }
        opener.accept(selected);
        return true;
    }

    /**
     * @return true if the two describe the same source: the same path, or, without a path, the same source reference.
     */
    static boolean sameSource(@NotNull Source a, @NotNull Source b) {
        if (a.getPath() != null || b.getPath() != null) {
            return Objects.equals(a.getPath(), b.getPath());
        }
        return a.getSourceReference() != null && a.getSourceReference() > 0
                && Objects.equals(a.getSourceReference(), b.getSourceReference());
    }

    private static @NotNull String key(@NotNull Source source) {
        return source.getPath() != null ? source.getPath() : name(source);
    }

    /**
     * @return the name of the source: its name, or else its file's.
     */
    public static @NotNull String name(@NotNull Source source) {
        if (source.getName() != null) {
            return source.getName();
        }
        if (source.getPath() != null) {
            Path name = Path.of(source.getPath()).getFileName();
            return name != null ? name.toString() : source.getPath();
        }
        return "";
    }

    private static class NameColumnInfo extends ColumnInfo<Source, String> {
        NameColumnInfo() {
            super(DAPBundle.message("dap.panels.loaded.sources.name"));
        }

        @Override
        public @Nullable String valueOf(Source source) {
            return name(source);
        }
    }

    private static class PathColumnInfo extends ColumnInfo<Source, String> {
        PathColumnInfo() {
            super(DAPBundle.message("dap.panels.loaded.sources.path"));
        }

        @Override
        public @Nullable String valueOf(Source source) {
            return source.getPath() != null ? source.getPath() : source.getOrigin();
        }
    }
}

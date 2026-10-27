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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Loaded Sources panel, which shows the sources that the DAP server reports as loaded
 * (<a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Requests_LoadedSources">loadedSources</a>
 * and the <a href="https://microsoft.github.io/debug-adapter-protocol//specification.html#Events_LoadedSource">loadedSource</a>
 * event): each one's name and path, in path order. Double-clicking a source, or Enter, opens it. Each DAP session of
 * the debug session, such as a child session started with 'startDebugging', keeps its own sources.
 *
 * @param <S> the DAP session which reports the sources.
 */
public class LoadedSourcesPanel<S> extends BorderLayoutPanel implements Disposable {

    public static final @NotNull String ID = "dap-loaded-sources-panel";

    private static final Comparator<SessionSource<?>> ORDER = Comparator.comparing(shown -> key(shown.source()));

    /**
     * A loaded source, and the DAP session which reported it: a source reference is valid only in its session.
     *
     * @param session the DAP session which reported the source.
     * @param source  the source.
     * @param <S>     the DAP session.
     */
    public record SessionSource<S>(@NotNull S session, @NotNull Source source) {
    }

    private final ListTableModel<SessionSource<S>> model;
    private final TableView<SessionSource<S>> table;

    /**
     * @param opener opens a source of the panel, by its path or its source reference, in the DAP session which
     *               reported it.
     */
    public LoadedSourcesPanel(@NotNull BiConsumer<S, Source> opener) {
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
     * Shows the given sources of a DAP session in place of those it reported before. The sources of the other DAP
     * sessions stay.
     *
     * @param session the DAP session.
     * @param sources the sources which the DAP session reports as loaded.
     */
    public void refresh(@NotNull S session, @NotNull Source[] sources) {
        List<Source> reported = List.of(sources);
        ApplicationManager.getApplication().invokeLater(() -> {
            List<SessionSource<S>> updated = new ArrayList<>(model.getItems());
            updated.removeIf(shown -> shown.session() == session);
            reported.forEach(source -> updated.add(new SessionSource<>(session, source)));
            updated.sort(ORDER);
            model.setItems(updated);
        });
    }

    /**
     * Updates the shown sources of a DAP session with its 'loadedSource' event: a new source is added, a changed one
     * replaces the same source, and a removed one is removed.
     *
     * @param session the DAP session.
     * @param args    the 'loadedSource' event.
     */
    public void update(@NotNull S session, @NotNull LoadedSourceEventArguments args) {
        Source source = args.getSource();
        if (source == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            List<SessionSource<S>> updated = new ArrayList<>(model.getItems());
            updated.removeIf(shown -> shown.session() == session && sameSource(shown.source(), source));
            if (!LoadedSourceEventArgumentsReason.REMOVED.equals(args.getReason())) {
                updated.add(new SessionSource<>(session, source));
                updated.sort(ORDER);
            }
            model.setItems(updated);
        });
    }

    /**
     * @return the shown sources, in order.
     */
    public @NotNull List<Source> getSources() {
        return model.getItems().stream().map(SessionSource::source).toList();
    }

    private boolean open(@NotNull BiConsumer<S, Source> opener) {
        SessionSource<S> selected = table.getSelectedObject();
        if (selected == null) {
            return false;
        }
        opener.accept(selected.session(), selected.source());
        return true;
    }

    /**
     * @return true if the two describe the same source: the same path, or, without a path, the same source reference,
     * or, without either, the same name.
     */
    static boolean sameSource(@NotNull Source a, @NotNull Source b) {
        if (a.getPath() != null || b.getPath() != null) {
            return Objects.equals(a.getPath(), b.getPath());
        }
        if (hasSourceReference(a) || hasSourceReference(b)) {
            return Objects.equals(a.getSourceReference(), b.getSourceReference());
        }
        // The name is all that tells apart two sources without a path or a source reference
        return a.getName() != null && a.getName().equals(b.getName());
    }

    private static boolean hasSourceReference(@NotNull Source source) {
        return source.getSourceReference() != null && source.getSourceReference() > 0;
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
        String path = source.getPath();
        if (path != null) {
            // The path is the DAP server's, such as a URL, which may not be a valid path of this file system
            String name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
            return !name.isEmpty() ? name : path;
        }
        return "";
    }

    private static class NameColumnInfo extends ColumnInfo<SessionSource<?>, String> {
        NameColumnInfo() {
            super(DAPBundle.message("dap.panels.loaded.sources.name"));
        }

        @Override
        public @Nullable String valueOf(SessionSource<?> shown) {
            return name(shown.source());
        }
    }

    private static class PathColumnInfo extends ColumnInfo<SessionSource<?>, String> {
        PathColumnInfo() {
            super(DAPBundle.message("dap.panels.loaded.sources.path"));
        }

        @Override
        public @Nullable String valueOf(SessionSource<?> shown) {
            Source source = shown.source();
            return source.getPath() != null ? source.getPath() : source.getOrigin();
        }
    }
}

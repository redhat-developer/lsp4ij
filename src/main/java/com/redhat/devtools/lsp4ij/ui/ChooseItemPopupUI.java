/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A reusable popup UI component that displays a searchable list of items of type {@code T}.
 * <p>
 * This component allows customization of how items are identified, displayed, and selected.
 * It provides automatic search/filtering via IntelliJ’s {@link IPopupChooserBuilder}
 * and highlights matching text.
 * </p>
 *
 * @param <T> the type of items displayed in the list
 *
 */
public class ChooseItemPopupUI<T> {

    private final @NotNull String title;
    private final @NotNull List<T> items;
    private final @NotNull Function<T, String> idProvider;
    private final @NotNull Function<T, String> nameProvider;
    private final @NotNull Consumer<T> onItemSelected;
    private JBPopup popup;

    public ChooseItemPopupUI(@NotNull String title,
                             @NotNull List<T> items,
                             @NotNull Function<T, String> idProvider,
                             @NotNull Function<T, String> nameProvider,
                             @NotNull Consumer<T> onItemSelected) {
        this.title = title;
        this.items = items;
        this.idProvider = idProvider;
        this.nameProvider = nameProvider;
        this.onItemSelected = onItemSelected;
    }

    public void show(@NotNull Component parent) {
        IPopupChooserBuilder<T> builder = JBPopupFactory.getInstance()
                .createPopupChooserBuilder(items)
                .setRenderer(new ColoredListCellRenderer<>() {
                    @Override
                    protected void customizeCellRenderer(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
                        append(nameProvider.apply(value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        append(idProvider.apply(value), SimpleTextAttributes.GRAY_ATTRIBUTES);
                        // Highlight matching part
                        SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected);
                    }
                })
                .setTitle(title)
                .setMovable(true)
                .setResizable(true)
                .setRequestFocus(true)
                .setNamerForFiltering(nameProvider::apply)
                .setItemsChosenCallback(selectedElements -> {
                    if (!selectedElements.isEmpty()) {
                        onItemSelected.accept(selectedElements.iterator().next());
                        if (popup != null) popup.closeOk(null);
                    }
                });

        popup = builder.createPopup();

        // Get the button's location on screen
        Point location = parent.getLocationOnScreen();
        // Calculate position just to the right of the button
        int x = location.x + parent.getWidth();
        int y = location.y;
        // Show the popup at the calculated screen coordinates
        popup.showInScreenCoordinates(parent, new Point(x, y));
    }
}

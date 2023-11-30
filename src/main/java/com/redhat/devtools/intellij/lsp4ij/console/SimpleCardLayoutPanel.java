/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.console;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.JBInsets;

import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JPanel;

abstract class SimpleCardLayoutPanel<V extends JComponent> extends JPanel implements Disposable {

    protected volatile boolean isDisposed = false;

    private CardLayout cardLayout;

    public SimpleCardLayoutPanel() {
        this(new CardLayout());
    }

    public SimpleCardLayoutPanel(CardLayout cardLayout) {
        super(cardLayout);
        this.cardLayout = cardLayout;
    }

    private Component visibleComponent() {
        for (var component : getComponents()) {
            if (component.isVisible()) return component;
        }
        return null;
    }

    public void show(String name) {
        cardLayout.show(this, name);
    }

    @Override
    public void dispose() {
        if (!isDisposed) {
            isDisposed = true;
            removeAll();
        }
    }

    @Override
    public void doLayout() {
        var bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());
        for (var component : getComponents()) {
            component.setBounds(bounds);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        var component = isPreferredSizeSet() ? null : visibleComponent();
        if (component == null) {
            return super.getPreferredSize();
        }
        // preferred size of a visible component plus border insets of this panel
        var size = component.getPreferredSize();
        JBInsets.addTo(size, getInsets()); // add border of this panel
        return size;
    }

    @Override
    public Dimension getMinimumSize() {
        var component = isMinimumSizeSet() ? null : visibleComponent();
        if (component == null) {
            return super.getMinimumSize();
        }
        // minimum size of a visible component plus border insets of this panel
        var size = component.getMinimumSize();
        JBInsets.addTo(size, getInsets());
        return size;
    }
}
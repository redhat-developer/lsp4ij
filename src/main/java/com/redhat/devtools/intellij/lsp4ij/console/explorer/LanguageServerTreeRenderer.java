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
package com.redhat.devtools.intellij.lsp4ij.console.explorer;

import com.intellij.ide.ui.UISettings;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.intellij.lsp4ij.ServerStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Language Server tree nodes renderer.
 * <p>
 * Some piece of code has been copied/pasted from https://github.com/JetBrains/intellij-community/blob/master/platform/smRunner/src/com/intellij/execution/testframework/sm/runner/ui/TestTreeRenderer.java and adapted for Language Server case.
 */
public class LanguageServerTreeRenderer extends ColoredTreeCellRenderer {

    @NonNls
    private static final String SPACE_STRING = " ";
    private String myDurationText;
    private Color myDurationColor;
    private int myDurationWidth;
    private int myDurationOffset;

    @Override
    public void customizeCellRenderer(@NotNull final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
        myDurationText = null;
        myDurationColor = null;
        myDurationWidth = 0;
        myDurationOffset = 0;

        if (value instanceof LanguageServerTreeNode) {
            // Render of language server
            LanguageServerTreeNode languageServerTreeNode = (LanguageServerTreeNode) value;
            setIcon(languageServerTreeNode.getIcon());
            append(languageServerTreeNode.getDisplayName());
            return;
        }

        if (value instanceof LanguageServerProcessTreeNode) {
            // Render of language server process
            LanguageServerProcessTreeNode languageProcessTreeNode = (LanguageServerProcessTreeNode) value;
            setIcon(languageProcessTreeNode.getIcon());
            append(languageProcessTreeNode.getDisplayName());

            if (languageProcessTreeNode.getServerStatus() == ServerStatus.starting
                    || languageProcessTreeNode.getServerStatus() == ServerStatus.stopping) {
                // Display elapsed time when language server is starting/stopping
                myDurationText = languageProcessTreeNode.getElapsedTime();
                final var durationText = myDurationText;
                if (durationText != null) {
                    FontMetrics metrics = getFontMetrics(RelativeFont.SMALL.derive(getFont()));
                    myDurationWidth = metrics.stringWidth(durationText);
                    myDurationOffset = metrics.getHeight() / 2; // an empty area before and after the text
                    myDurationColor = selected ? UIUtil.getTreeSelectionForeground(hasFocus) : SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor();
                }
            }
            return;
        }
        //strange node
        final String text = value.toString();
        //no icon
        append(text != null ? text : SPACE_STRING, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
        final Dimension preferredSize = super.getPreferredSize();
        if (myDurationWidth > 0) preferredSize.width += myDurationWidth + myDurationOffset;
        return preferredSize;
    }

    @Override
    protected void paintComponent(Graphics g) {
        UISettings.setupAntialiasing(g);
        Shape clip = null;
        int width = getWidth();
        int height = getHeight();
        if (isOpaque()) {
            // paint background for expanded row
            g.setColor(getBackground());
            g.fillRect(0, 0, width, height);
        }
        if (myDurationWidth > 0) {
            width -= myDurationWidth + myDurationOffset;
            if (width > 0 && height > 0) {
                g.setColor(myDurationColor);
                g.setFont(RelativeFont.SMALL.derive(getFont()));
                g.drawString(myDurationText, width + myDurationOffset / 2, getTextBaseLine(g.getFontMetrics(), height));
                clip = g.getClip();
                g.clipRect(0, 0, width, height);
            }
        }
        super.paintComponent(g);
        // restore clip area if needed
        if (clip != null) g.setClip(clip);
    }
}


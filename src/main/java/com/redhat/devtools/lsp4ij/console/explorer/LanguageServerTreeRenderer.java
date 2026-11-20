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
package com.redhat.devtools.lsp4ij.console.explorer;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.ServerStatus;
import org.eclipse.lsp4j.ServerInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private final @Nullable Project project;
    private String myDurationText;
    private Color myDurationColor;
    private int myDurationWidth;
    private int myDurationOffset;

    public LanguageServerTreeRenderer(@Nullable Project project) {
        this.project = project;
    }

    public LanguageServerTreeRenderer() {
        this(null);
    }

    @Override
    public void customizeCellRenderer(@NotNull final JTree tree,
                                      final Object value,
                                      final boolean selected,
                                      final boolean expanded,
                                      final boolean leaf,
                                      final int row,
                                      final boolean hasFocus) {
        setToolTipText(null);
        myDurationText = null;
        myDurationColor = null;
        myDurationWidth = 0;
        myDurationOffset = 0;

        if (value instanceof LanguageServerTreeNode languageServerTreeNode) {
            // Render of language server
            setIcon(languageServerTreeNode.getIcon());
            append(languageServerTreeNode.getDisplayName());
            Project p = project != null ? project : languageServerTreeNode.getProject();
            String badge = languageServerTreeNode.getStatusBadge(p);
            if (badge != null) {
                append(" " + badge, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            setToolTipText(languageServerTreeNode.getToolTipText(p));
            return;
        }

        if (value instanceof LanguageServerProcessTreeNode languageProcessTreeNode) {
            // Render of language server process
            setIcon(languageProcessTreeNode.getIcon());
            append(languageProcessTreeNode.getDisplayName());
            String secondaryText = languageProcessTreeNode.getSecondaryText();
            if (secondaryText != null) {
                append(" [" + secondaryText + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            setToolTipText(languageProcessTreeNode.getToolTipText());

            if (languageProcessTreeNode.getServerStatus() == ServerStatus.starting
                    || languageProcessTreeNode.getServerStatus() == ServerStatus.stopping
                    || languageProcessTreeNode.getServerStatus() == ServerStatus.checking_installed
                    || languageProcessTreeNode.getServerStatus() == ServerStatus.installing) {
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

    /**
     * Formats the server info for tooltip display.
     *
     * @param serverInfo the server info returned by the language server, or {@code null}
     * @return formatted server info, or {@code null} if no info is available
     */
    @Nullable
    static String formatServerInfo(@Nullable ServerInfo serverInfo) {
        if (serverInfo == null) {
            return null;
        }
        String name = serverInfo.getName();
        String version = serverInfo.getVersion();
        boolean hasName = name != null && !name.isBlank();
        boolean hasVersion = version != null && !version.isBlank();

        if (hasName && hasVersion) {
            return name.trim() + " " + version.trim();
        }
        if (hasName) {
            return name.trim();
        }
        if (hasVersion) {
            return version.trim();
        }
        return null;
    }
}


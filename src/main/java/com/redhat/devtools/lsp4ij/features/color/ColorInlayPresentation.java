/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.color;

import com.intellij.codeInsight.hints.presentation.BasePresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@link com.intellij.codeInsight.hints.presentation.InlayPresentation} implementation to draw a colorized square.
 */
public class ColorInlayPresentation extends BasePresentation {

    private final Color background;

    private final int squareDimension;

    public ColorInlayPresentation(Color background, @NotNull PresentationFactory factory) {
        this.background = background;
        // The dimension of the colorized square height/width to display is the font leading
        // This information can be taken from InlayTextMetrics, but it is not a public API
        // To consume InlayTextMetrics#getFontHeight, we create a TextPlaceholderPresentation instance and call the getHeight
        this.squareDimension = factory.textSpacePlaceholder(0, false).getHeight();
    }

    @Override
    public int getHeight() {
        return getSquareDimension();
    }

    @Override
    public int getWidth() {
        return getSquareDimension() + 4;
    }

    private int getSquareDimension() {
        return squareDimension;
    }

    @Override
    public void paint(@NotNull Graphics2D g, @NotNull TextAttributes textAttributes) {
        Color preservedBackground = g.getBackground();

        int x = 0;
        int y = g.getFontMetrics().getHeight() - getSquareDimension() - 1;

        // Fill rectangle with the given background
        g.setColor(background);
        g.fillRect(x, y, getSquareDimension(), getSquareDimension());

        // Draw a border
        g.setColor(Gray.x00.withAlpha(40));//Same border color as com.intellij.util.ui.ColorIcon
        int borderWidth = 1;
        g.setStroke(new BasicStroke(borderWidth));
        g.drawRect(x, y, getSquareDimension(), getSquareDimension());

        g.setColor(preservedBackground);
    }
}

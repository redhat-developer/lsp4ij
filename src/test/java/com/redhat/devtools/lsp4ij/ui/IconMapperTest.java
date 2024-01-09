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
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.ColorIcon;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.junit.Test;

import javax.swing.*;

import static com.redhat.devtools.lsp4ij.ui.IconMapper.getIcon;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IconMapperTest {

    @Test
    public void getIconTest()  {
        assertNull(getIcon((CompletionItemKind)null));
        for (CompletionItemKind value : CompletionItemKind.values()) {
            assertNotNull(getIcon(value), "Missing matching icon for "+value);
        }
    }

    @Test
    public void getColorIconTest()  {
        String color = "#FFFFFF";

        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Color);

        //Test string documentation
        item.setDocumentation(color);
        Icon whiteIcon = getIcon(item);
        assertNotNull(whiteIcon, "Should have an icon");
        assertNotEquals(AllIcons.Nodes.EmptyNode, whiteIcon);
        assertTrue(whiteIcon instanceof ColorIcon, "Unexpected icon instance: "+ whiteIcon.getClass());

        //Lower case color
        item.setDocumentation(color.toLowerCase());
        Icon sameWhiteIcon = getIcon(item);
        assertEquals(whiteIcon, sameWhiteIcon);

        //Other color
        item.setDocumentation("#000000");
        Icon blackIcon = getIcon(item);
        assertNotEquals(whiteIcon, blackIcon);

        //MarkupContent
        item = new CompletionItem();
        item.setKind(CompletionItemKind.Color);
        item.setDocumentation(new MarkupContent("markdown", color ));
        Icon markdownIcon = getIcon(item);
        assertEquals(whiteIcon, markdownIcon);


        //Label
        item = new CompletionItem();
        item.setKind(CompletionItemKind.Color);
        item.setLabel(color);
        Icon labelIcon = getIcon(item);
        assertEquals(whiteIcon, labelIcon);

        //Invalid color
        item = new CompletionItem();
        item.setKind(CompletionItemKind.Color);
        item.setLabel("#Nooope");
        Icon badColorIcon = getIcon(item);
        assertEquals(AllIcons.Nodes.EmptyNode, badColorIcon);
    }
}

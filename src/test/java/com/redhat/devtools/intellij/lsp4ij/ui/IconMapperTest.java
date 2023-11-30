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
package com.redhat.devtools.intellij.lsp4ij.ui;

import org.eclipse.lsp4j.CompletionItemKind;
import org.junit.Test;

import static com.redhat.devtools.intellij.lsp4ij.ui.IconMapper.getIcon;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IconMapperTest {

    @Test
    public void getIconTest()  {
        assertNull(getIcon(null));
        for (CompletionItemKind value : CompletionItemKind.values()) {
            assertNotNull(getIcon(value), "Missing matching icon for "+value);
        }
    }
}

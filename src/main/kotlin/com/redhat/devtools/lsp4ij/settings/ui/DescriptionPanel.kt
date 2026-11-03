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
@file:JvmName("DescriptionPanel")

package com.redhat.devtools.lsp4ij.settings.ui

import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Creates the panel that shows the description of a language server or a debug adapter above its settings,
 * as a comment of the Kotlin UI DSL that wraps at [DEFAULT_COMMENT_WIDTH] characters, as the comment of
 * `ComponentPanelBuilder` did. A blank description gives an empty panel of size 0.
 */
fun create(description: String?): JComponent {
    val text = description?.trim().orEmpty()
    if (text.isEmpty()) {
        return JPanel().apply {
            minimumSize = JBUI.emptySize()
            preferredSize = JBUI.emptySize()
        }
    }
    return panel {
        row {
            comment(text, maxLineLength = DEFAULT_COMMENT_WIDTH)
        }
    }
}

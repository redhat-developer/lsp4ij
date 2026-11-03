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
package com.redhat.devtools.lsp4ij.features.linkedEditingRange;

import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NotNull;

/**
 * Edits the linked editing ranges of each editor of a file together, with {@link LSPLinkedEditingRangeSynchronizer}.
 */
public class LSPLinkedEditingRangeEditorFactoryListener implements EditorFactoryListener {

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        LSPLinkedEditingRangeSynchronizer.install(event.getEditor());
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        LSPLinkedEditingRangeSynchronizer.uninstall(event.getEditor());
    }
}

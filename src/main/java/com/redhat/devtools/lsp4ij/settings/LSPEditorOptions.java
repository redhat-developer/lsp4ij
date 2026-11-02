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
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * The editor options of LSP features which apply to every language server, in Settings | Editor | General |
 * Smart Keys.
 */
@State(name = "LSPEditorOptions", storages = @Storage("lsp4ij.editor.xml"), category = SettingsCategory.CODE)
public final class LSPEditorOptions implements PersistentStateComponent<LSPEditorOptions.Options> {

    /**
     * The stored options.
     */
    public static class Options {
        /**
         * True if typing in a linked editing range edits the other ranges too.
         */
        public boolean linkedEditing = true;
    }

    private Options options = new Options();

    public static @NotNull LSPEditorOptions getInstance() {
        return ApplicationManager.getApplication().getService(LSPEditorOptions.class);
    }

    /**
     * Returns true if typing in a range which a language server links to others edits them too
     * (textDocument/linkedEditingRange), which is the default.
     *
     * @return true if linked editing is on.
     */
    public boolean isLinkedEditing() {
        return options.linkedEditing;
    }

    /**
     * Turns linked editing on or off.
     *
     * @param linkedEditing true to edit linked editing ranges together.
     */
    public void setLinkedEditing(boolean linkedEditing) {
        options.linkedEditing = linkedEditing;
    }

    @Override
    public @NotNull Options getState() {
        return options;
    }

    @Override
    public void loadState(@NotNull Options state) {
        options = state;
    }
}

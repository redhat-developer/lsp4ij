/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Validates document versions supplied by a language server before applying a workspace edit.
 */
@ApiStatus.Internal
public final class WorkspaceEditValidator {

    private WorkspaceEditValidator() {
    }

    /**
     * Returns a refusal reason if an edit targets an outdated version of a synchronized document.
     * The caller must keep write access from this check through application so typing cannot
     * invalidate a successful check. All targets are checked before any mutation.
     * <p>
     * Unversioned edits and files not synchronized with this server retain their existing behavior:
     * there is no LSP version to compare for those files. This does not validate resource operations.
     */
    public static @Nullable String validateVersions(@NotNull WorkspaceEdit edit,
                                                     @NotNull LanguageServerWrapper wrapper) {
        ApplicationManager.getApplication().assertWriteAccessAllowed();
        if (edit.getDocumentChanges() == null) {
            return null;
        }
        for (var change : edit.getDocumentChanges()) {
            if (change.isLeft()) {
                var identifier = change.getLeft().getTextDocument();
                Integer version = identifier.getVersion();
                if (version == null) {
                    continue;
                }
                var opened = wrapper.getOpenedDocument(URI.create(identifier.getUri()));
                var synchronizer = opened != null ? opened.getSynchronizer() : null;
                if (synchronizer != null && !synchronizer.isCurrentVersion(version)) {
                    return LanguageServerBundle.message("lsp.workspace.edit.version.changed", identifier.getUri());
                }
            }
        }
        return null;
    }
}

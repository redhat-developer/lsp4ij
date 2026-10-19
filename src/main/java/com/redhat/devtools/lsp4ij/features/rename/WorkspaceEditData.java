package com.redhat.devtools.lsp4ij.features.rename;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.WorkspaceEditSnapshot;
import org.eclipse.lsp4j.WorkspaceEdit;

/**
 * A rename response paired with the state captured before its request was sent.
 * Keep the snapshot with the response until the entire rename is ready to apply.
 */
public record WorkspaceEditData(WorkspaceEdit edit, LanguageServerItem languageServer, WorkspaceEditSnapshot snapshot) {
    /**
     * Compatibility constructor for edits created locally. Asynchronous requests must use
     * the three-argument constructor with a snapshot captured before dispatch.
     */
    public WorkspaceEditData(WorkspaceEdit edit, LanguageServerItem languageServer) {
        this(edit, languageServer, WorkspaceEditSnapshot.capture(languageServer));
    }
}

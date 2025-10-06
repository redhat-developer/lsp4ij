package com.redhat.devtools.lsp4ij.features.rename;

import org.eclipse.lsp4j.WorkspaceEdit;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
public record WorkspaceEditData(WorkspaceEdit edit, LanguageServerItem languageServer ) {
}
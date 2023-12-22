package com.redhat.devtools.lsp4ij.server.definition;

import com.redhat.devtools.lsp4ij.DocumentMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * internal class to capture content-type mappings for language servers
 */
public class ServerMapping {

    @NotNull
    private final String serverId;
    @NotNull
    private final String languageId;
    @NotNull
    private final DocumentMatcher documentMatcher;

    public ServerMapping(@NotNull String serverId, @NotNull String languageId, @NotNull DocumentMatcher documentMatcher) {
        this.serverId = serverId;
        this.languageId = languageId;
        this.documentMatcher = documentMatcher;
    }

    @NotNull
    public String getServerId() {
        return serverId;
    }

    @NotNull
    public String getLanguageId() {
        return languageId;
    }

    @NotNull
    public DocumentMatcher getDocumentMatcher() {
        return documentMatcher;
    }
}
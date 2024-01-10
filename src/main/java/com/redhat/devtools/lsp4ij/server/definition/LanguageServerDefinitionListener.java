package com.redhat.devtools.lsp4ij.server.definition;

import com.redhat.devtools.lsp4ij.LanguageServersRegistry;

public interface LanguageServerDefinitionListener {

    void handleAdded(LanguageServerDefinition definition);

    void handleRemoved(LanguageServerDefinition definition);
}

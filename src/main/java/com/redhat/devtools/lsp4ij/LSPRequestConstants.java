/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

/**
 * LSP request constants.
 */
public class LSPRequestConstants {

    // workspace/* LSP requests
    public static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
    public static final String WORKSPACE_WILL_CREATE_FILES = "workspace/willCreateFiles";
    public static final String WORKSPACE_WILL_DELETE_FILES = "workspace/willDeleteFiles";
    public static final String WORKSPACE_WILL_RENAME_FILES = "workspace/willRenameFiles";
    public static final String WORKSPACE_SYMBOL = "workspace/symbol";

    // textDocument/* LSP requests

    public static final String TEXT_DOCUMENT_DECLARATION = "textDocument/declaration";
    public static final String TEXT_DOCUMENT_DEFINITION = "textDocument/definition";
    public static final String TEXT_DOCUMENT_DOCUMENT_LINK = "textDocument/documentLink";
    public static final String TEXT_DOCUMENT_FOLDING_RANGE = "textDocument/foldingRange";
    public static final String TEXT_DOCUMENT_SELECTION_RANGE = "textDocument/selectionRange";
    public static final String TEXT_DOCUMENT_SEMANTIC_TOKENS_FULL = "textDocument/semanticTokens/full";
    public static final String TEXT_DOCUMENT_TYPE_DEFINITION = "textDocument/typeDefinition";
    public static final String TEXT_DOCUMENT_CODE_ACTION = "textDocument/codeAction";
    public static final String TEXT_DOCUMENT_CODE_LENS = "textDocument/codeLens";
    public static final String CODE_LENS_RESOLVE = "codelens/resolve";
    public static final String TEXT_DOCUMENT_HOVER = "textDocument/hover";
    public static final String TEXT_DOCUMENT_INLAY_HINT = "textDocument/inlayHint";
    public static final String INLAY_HINT_RESOLVE = "inlayHint/resolve";
    public static final String TEXT_DOCUMENT_DOCUMENT_COLOR = "textDocument/documentColor";
    public static final String TEXT_DOCUMENT_COMPLETION = "textDocument/completion";
    public static final String TEXT_DOCUMENT_DOCUMENT_HIGHLIGHT = "textDocument/documentHighlight";
    public static final String TEXT_DOCUMENT_FORMATTING = "textDocument/formatting";
    public static final String TEXT_DOCUMENT_RANGE_FORMATTING = "textDocument/rangeFormatting";
    public static final String TEXT_DOCUMENT_ON_TYPE_FORMATTING = "textDocument/onTypeFormatting";
    public static final String TEXT_DOCUMENT_IMPLEMENTATION = "textDocument/implementation";
    public static final String TEXT_DOCUMENT_REFERENCES = "textDocument/references";
    public static final String TEXT_DOCUMENT_SIGNATURE_HELP = "textDocument/signatureHelp";
    public static final String TEXT_DOCUMENT_PREPARE_RENAME = "textDocument/prepareRename";
    public static final String TEXT_DOCUMENT_RENAME = "textDocument/rename";
    public static final String TEXT_DOCUMENT_DOCUMENT_SYMBOL = "textDocument/documentSymbol";
    public static final String TEXT_DOCUMENT_CALL_HIERARCHY = "textDocument/callHierarchy";
    public static final String TEXT_DOCUMENT_PREPARE_CALL_HIERARCHY = "textDocument/prepareCallHierarchy";
    public static final String CALL_HIERARCHY_INCOMING_CALLS = "callHierarchy/incomingCalls";
    public static final String CALL_HIERARCHY_OUTGOING_CALLS = "callHierarchy/outgoingCalls";
    public static final String TEXT_DOCUMENT_PREPARE_TYPE_HIERARCHY = "textDocument/prepareTypeHierarchy";
    public static final String TYPE_HIERARCHY_SUB_TYPES = "typeHierarchy/subtypes";
    public static final String TYPE_HIERARCHY_SUPER_TYPES = "typeHierarchy/supertypes";
    public static final String TEXT_DOCUMENT_TYPE_HIERARCHY = "textDocument/typeHierarchy";

    private LSPRequestConstants() {

    }
}

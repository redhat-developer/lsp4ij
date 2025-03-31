/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *  Red Hat Inc. - rename SupportedFeatures to ClientCapabilitiesFactory
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Factory of LSP client capabilities.
 */
public class ClientCapabilitiesFactory {

    public static ClientCapabilities create(Object experimental) {
        ClientCapabilities clientCapabilities = new ClientCapabilities(
                getWorkspaceClientCapabilities(),
                getTextDocumentClientCapabilities(),
                getWindowClientCapabilities(),
                experimental);
        clientCapabilities.setGeneral(getGeneralClientCapabilities());
        return clientCapabilities;
    }

    private static GeneralClientCapabilities getGeneralClientCapabilities() {
        GeneralClientCapabilities generalCapabilities = new GeneralClientCapabilities();
        StaleRequestCapabilities staleRequestCapabilities = new StaleRequestCapabilities();
        staleRequestCapabilities.setCancel(true);
        generalCapabilities.setStaleRequestSupport(staleRequestCapabilities);
        generalCapabilities.setPositionEncodings(List.of(PositionEncodingKind.UTF16));
        return generalCapabilities;
    }

    private static @NotNull WorkspaceClientCapabilities getWorkspaceClientCapabilities() {
        final var workspaceClientCapabilities = new WorkspaceClientCapabilities();

        // Apply edit support
        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_applyEdit
        workspaceClientCapabilities.setApplyEdit(Boolean.TRUE);

        // Support for 'workspace/configuration'
        workspaceClientCapabilities.setConfiguration(Boolean.TRUE);

        // Support for 'workspace/symbol'
        var symbolCapabilities = new SymbolCapabilities();
        workspaceClientCapabilities.setSymbol(symbolCapabilities);

        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#executeCommandClientCapabilities
        workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities(Boolean.TRUE));
        // TODO
        // workspaceClientCapabilities.setSymbol(new SymbolCapabilities(Boolean.TRUE));
        workspaceClientCapabilities.setWorkspaceFolders(Boolean.TRUE);

        // Workspace edit support
        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceEditClientCapabilities
        WorkspaceEditCapabilities editCapabilities = new WorkspaceEditCapabilities();
        editCapabilities.setDocumentChanges(Boolean.TRUE);
        editCapabilities.setResourceOperations(List.of(
                ResourceOperationKind.Create,
                ResourceOperationKind.Delete,
                ResourceOperationKind.Rename));
        // editCapabilities.setFailureHandling(FailureHandlingKind.Undo);
        workspaceClientCapabilities.setWorkspaceEdit(editCapabilities);

        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#didChangeWatchedFilesClientCapabilities
        workspaceClientCapabilities.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities(Boolean.TRUE));

        // File operations support
        FileOperationsWorkspaceCapabilities fileOperationsWorkspaceCapabilities = new FileOperationsWorkspaceCapabilities();
        fileOperationsWorkspaceCapabilities.setDynamicRegistration(Boolean.TRUE);
        //fileOperationsWorkspaceCapabilities.setWillCreate(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willCreateFiles
        //fileOperationsWorkspaceCapabilities.setDidCreate(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didCreateFiles
        //fileOperationsWorkspaceCapabilities.setWillDelete(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles
        //fileOperationsWorkspaceCapabilities.setDidDelete(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didDeleteFiles
        fileOperationsWorkspaceCapabilities.setWillRename(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles
        fileOperationsWorkspaceCapabilities.setDidRename(Boolean.TRUE); // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didRenameFiles
        workspaceClientCapabilities.setFileOperations(fileOperationsWorkspaceCapabilities);

        // DidChangeConfiguration support
        // See https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#didChangeConfigurationClientCapabilities
        workspaceClientCapabilities.setDidChangeConfiguration(new DidChangeConfigurationCapabilities(Boolean.TRUE));

        // Refresh support for CodeLens
        workspaceClientCapabilities.setCodeLens(new CodeLensWorkspaceCapabilities(Boolean.TRUE));

        // Refresh support for InlayHint
        workspaceClientCapabilities.setInlayHint(new InlayHintWorkspaceCapabilities(Boolean.TRUE));

        // Refresh support for SemanticTokens
        workspaceClientCapabilities.setSemanticTokens(new SemanticTokensWorkspaceCapabilities(Boolean.TRUE));

        return workspaceClientCapabilities;
    }


    private static @NotNull TextDocumentClientCapabilities getTextDocumentClientCapabilities() {
        final var textDocumentClientCapabilities = new TextDocumentClientCapabilities();

        // Support for 'textDocument/publishDiagnostics'
        final var publishDiagnosticsCapabilities = new PublishDiagnosticsCapabilities();
        publishDiagnosticsCapabilities.setDataSupport(Boolean.TRUE);
        publishDiagnosticsCapabilities.setCodeDescriptionSupport(Boolean.TRUE);
        publishDiagnosticsCapabilities.setRelatedInformation(Boolean.TRUE);
        DiagnosticsTagSupport tagSupport = new DiagnosticsTagSupport(List.of(DiagnosticTag.Unnecessary, DiagnosticTag.Deprecated));
        publishDiagnosticsCapabilities.setTagSupport(tagSupport);
        textDocumentClientCapabilities.setPublishDiagnostics(publishDiagnosticsCapabilities);

        // Support for 'textDocument/diagnostic'
        final var diagnosticCapabilities = new DiagnosticCapabilities();
        diagnosticCapabilities.setDynamicRegistration(Boolean.TRUE);
        diagnosticCapabilities.setRelatedDocumentSupport(Boolean.TRUE);
        textDocumentClientCapabilities.setDiagnostic(diagnosticCapabilities);

        // Code Action support
        final var codeAction = new CodeActionCapabilities(new CodeActionLiteralSupportCapabilities(
                new CodeActionKindCapabilities(List.of(
                        CodeActionKind.QuickFix,
                        CodeActionKind.Refactor,
                        CodeActionKind.RefactorExtract,
                        CodeActionKind.RefactorInline,
                        CodeActionKind.RefactorRewrite,
                        CodeActionKind.Source,
                        CodeActionKind.SourceOrganizeImports))),
                Boolean.TRUE);
        codeAction.setDataSupport(Boolean.TRUE);
        codeAction.setResolveSupport(new CodeActionResolveSupportCapabilities(List.of("edit")));
        textDocumentClientCapabilities.setCodeAction(codeAction);

        // Code Lens support
        textDocumentClientCapabilities.setCodeLens(new CodeLensCapabilities(Boolean.TRUE));

        // Inlay Hint support
        textDocumentClientCapabilities.setInlayHint(new InlayHintCapabilities(Boolean.TRUE));

        // Color support
        textDocumentClientCapabilities.setColorProvider(new ColorProviderCapabilities(Boolean.TRUE));

        // Completion support
        final var completionItemCapabilities = new CompletionItemCapabilities(Boolean.TRUE);
        completionItemCapabilities
                .setDocumentationFormat(List.of(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
        completionItemCapabilities.setInsertTextModeSupport(new CompletionItemInsertTextModeSupportCapabilities(List.of(InsertTextMode.AsIs, InsertTextMode.AdjustIndentation)));
        completionItemCapabilities.setResolveSupport(new CompletionItemResolveSupportCapabilities(
                List.of(
                        "documentation",
                        "detail",
                        "additionalTextEdits")));
        completionItemCapabilities.setDeprecatedSupport(Boolean.TRUE);
        completionItemCapabilities.setLabelDetailsSupport(Boolean.TRUE);
        CompletionCapabilities completionCapabilities = new CompletionCapabilities(completionItemCapabilities);
        completionCapabilities.setCompletionList(new CompletionListCapabilities(List.of("editRange")));
        completionCapabilities.setDynamicRegistration(Boolean.TRUE);
        textDocumentClientCapabilities.setCompletion(completionCapabilities);

        // Signature help support
        SignatureHelpCapabilities signatureHelpCapabilities = new SignatureHelpCapabilities(Boolean.TRUE);
        SignatureInformationCapabilities signatureInformationCapabilities = new SignatureInformationCapabilities();
        ParameterInformationCapabilities parameterInformationCapabilities = new ParameterInformationCapabilities();
        parameterInformationCapabilities.setLabelOffsetSupport(Boolean.TRUE);
        signatureInformationCapabilities.setParameterInformation(parameterInformationCapabilities);
        signatureHelpCapabilities.setSignatureInformation(signatureInformationCapabilities);
        textDocumentClientCapabilities.setSignatureHelp(signatureHelpCapabilities);

        // Declaration support
        final var declarationCapabilities = new DeclarationCapabilities(Boolean.TRUE);
        declarationCapabilities.setLinkSupport(Boolean.TRUE);
        textDocumentClientCapabilities.setDeclaration(declarationCapabilities);

        // Definition support
        final var definitionCapabilities = new DefinitionCapabilities(Boolean.TRUE);
        definitionCapabilities.setLinkSupport(Boolean.TRUE);
        textDocumentClientCapabilities.setDefinition(definitionCapabilities);

        // Type Definition support
        final var typeDefinitionCapabilities = new TypeDefinitionCapabilities(Boolean.TRUE);
        typeDefinitionCapabilities.setLinkSupport(Boolean.TRUE);
        textDocumentClientCapabilities.setTypeDefinition(typeDefinitionCapabilities);

        // DocumentHighlight support
        textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities(Boolean.TRUE));

        // DocumentLink support
        textDocumentClientCapabilities.setDocumentLink(new DocumentLinkCapabilities(Boolean.TRUE));

        // textDocument/documentSymbol
        final var documentSymbol = new DocumentSymbolCapabilities(Boolean.TRUE);
        documentSymbol.setHierarchicalDocumentSymbolSupport(true);
        documentSymbol.setSymbolKind(new SymbolKindCapabilities(Arrays.asList(SymbolKind.Array,
                SymbolKind.Boolean, SymbolKind.Class, SymbolKind.Constant, SymbolKind.Constructor,
                SymbolKind.Enum, SymbolKind.EnumMember, SymbolKind.Event, SymbolKind.Field, SymbolKind.File,
                SymbolKind.Function, SymbolKind.Interface, SymbolKind.Key, SymbolKind.Method, SymbolKind.Module,
                SymbolKind.Namespace, SymbolKind.Null, SymbolKind.Number, SymbolKind.Object,
                SymbolKind.Operator, SymbolKind.Package, SymbolKind.Property, SymbolKind.String,
                SymbolKind.Struct, SymbolKind.TypeParameter, SymbolKind.Variable)));
        textDocumentClientCapabilities.setDocumentSymbol(documentSymbol);

        // FoldingRange support
        var foldingRangeCapabilities = new FoldingRangeCapabilities();
        foldingRangeCapabilities.setDynamicRegistration(Boolean.TRUE);
        textDocumentClientCapabilities.setFoldingRange(foldingRangeCapabilities);

        // Hover support
        final var hoverCapabilities = new HoverCapabilities(Boolean.TRUE);
        hoverCapabilities.setContentFormat(List.of(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
        textDocumentClientCapabilities.setHover(hoverCapabilities);

        // References support
        textDocumentClientCapabilities.setReferences(new ReferencesCapabilities(Boolean.TRUE));

        // Implementation support
        var implementationCapabilities = new ImplementationCapabilities(Boolean.TRUE);
        implementationCapabilities.setLinkSupport(Boolean.TRUE);
        textDocumentClientCapabilities.setImplementation(implementationCapabilities);

        // textDocument/formatting
        textDocumentClientCapabilities.setFormatting(new FormattingCapabilities(Boolean.TRUE));

        // textDocument/rangeFormatting
        textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities(Boolean.TRUE));

        // textDocument/rename support
        final var renameCapabilities = new RenameCapabilities(Boolean.TRUE);
        renameCapabilities.setPrepareSupport(true);
        textDocumentClientCapabilities.setRename(renameCapabilities);

        // textDocument/semanticTokens
        var semanticTokensCapabilities = new SemanticTokensCapabilities(Boolean.TRUE);
        semanticTokensCapabilities.setTokenTypes(List.of(
                SemanticTokenTypes.Namespace,
                SemanticTokenTypes.Type,
                SemanticTokenTypes.Class,
                SemanticTokenTypes.Enum,
                SemanticTokenTypes.Interface,
                SemanticTokenTypes.Struct,
                SemanticTokenTypes.TypeParameter,
                SemanticTokenTypes.Parameter,
                SemanticTokenTypes.Variable,
                SemanticTokenTypes.Property,
                SemanticTokenTypes.EnumMember,
                SemanticTokenTypes.Event,
                SemanticTokenTypes.Function,
                SemanticTokenTypes.Method,
                SemanticTokenTypes.Macro,
                SemanticTokenTypes.Keyword,
                SemanticTokenTypes.Modifier,
                SemanticTokenTypes.Comment,
                SemanticTokenTypes.String,
                SemanticTokenTypes.Number,
                SemanticTokenTypes.Regexp,
                SemanticTokenTypes.Operator,
                SemanticTokenTypes.Decorator,
                "label"
        ));
        semanticTokensCapabilities.setTokenModifiers(List.of(
                SemanticTokenModifiers.Declaration,
                SemanticTokenModifiers.Definition,
                SemanticTokenModifiers.Readonly,
                SemanticTokenModifiers.Static
                /*"deprecated",
                "abstract",
                "async",
                "modification",
                "documentation",
                "defaultLibrary"*/
        ));
        semanticTokensCapabilities.setMultilineTokenSupport(Boolean.TRUE);
        semanticTokensCapabilities.setServerCancelSupport(Boolean.TRUE);
        var semanticTokensClientCapabilitiesRequests = new SemanticTokensClientCapabilitiesRequests(Boolean.TRUE, Boolean.FALSE);
        semanticTokensCapabilities.setFormats(List.of(TokenFormat.Relative));
        semanticTokensCapabilities.setRequests(semanticTokensClientCapabilitiesRequests);
        textDocumentClientCapabilities.setSemanticTokens(semanticTokensCapabilities);

        // Synchronization support
        textDocumentClientCapabilities
                .setSynchronization(new SynchronizationCapabilities(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE));

        // Call Hierarchy support
        textDocumentClientCapabilities.setCallHierarchy(new CallHierarchyCapabilities(Boolean.TRUE));

        // Type Hierarchy support
        textDocumentClientCapabilities.setTypeHierarchy(new TypeHierarchyCapabilities(Boolean.TRUE));

        // TODO
        // SelectionRangeCapabilities selectionRange = new SelectionRangeCapabilities();
        // textDocumentClientCapabilities.setSelectionRange(selectionRange);
        return textDocumentClientCapabilities;
    }

    private static WindowClientCapabilities getWindowClientCapabilities() {
        final var windowClientCapabilities = new WindowClientCapabilities();
        windowClientCapabilities.setShowDocument(new ShowDocumentCapabilities(true));
        /**
         * LSP4IJ supports server initiated progress using the
         * `window/workDoneProgress/create` request.
         */
        windowClientCapabilities.setWorkDoneProgress(true);
        windowClientCapabilities.setShowMessage(new WindowShowMessageRequestCapabilities());
        return windowClientCapabilities;

    }

}

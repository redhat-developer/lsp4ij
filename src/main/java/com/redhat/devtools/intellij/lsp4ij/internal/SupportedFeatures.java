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
 *
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.internal;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.*;

import javax.annotation.Nonnull;

/**
 *
 */
public class SupportedFeatures {

	public static @Nonnull TextDocumentClientCapabilities getTextDocumentClientCapabilities() {
		final var textDocumentClientCapabilities = new TextDocumentClientCapabilities();

		// Code Action support
		final var codeAction = new CodeActionCapabilities(new CodeActionLiteralSupportCapabilities(
				new CodeActionKindCapabilities(Arrays.asList(CodeActionKind.QuickFix, CodeActionKind.Refactor,
						CodeActionKind.RefactorExtract, CodeActionKind.RefactorInline,
						CodeActionKind.RefactorRewrite, CodeActionKind.Source,
						CodeActionKind.SourceOrganizeImports))),
				true);
		codeAction.setDataSupport(true);
		codeAction.setResolveSupport(new CodeActionResolveSupportCapabilities(List.of("edit")));
		textDocumentClientCapabilities.setCodeAction(codeAction);

		// Code Lens support
		textDocumentClientCapabilities.setCodeLens(new CodeLensCapabilities());

		// Inlay Hint support
		textDocumentClientCapabilities.setInlayHint(new InlayHintCapabilities());

		// TODO : support textDocument/colorPresentation
		// textDocumentClientCapabilities.setColorProvider(new ColorProviderCapabilities());

		// Completion support
		final var completionItemCapabilities = new CompletionItemCapabilities(Boolean.TRUE);
		completionItemCapabilities
				.setDocumentationFormat(Arrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
		completionItemCapabilities.setInsertTextModeSupport(new CompletionItemInsertTextModeSupportCapabilities(List.of(InsertTextMode.AsIs, InsertTextMode.AdjustIndentation)));

		completionItemCapabilities.setResolveSupport(new CompletionItemResolveSupportCapabilities(List.of("documentation" /*, "detail", "additionalTextEdits" */)));
		CompletionCapabilities completionCapabilities = new CompletionCapabilities(completionItemCapabilities);
		completionCapabilities.setCompletionList(new CompletionListCapabilities(List.of("editRange")));
		textDocumentClientCapabilities.setCompletion(completionCapabilities);

		// Definition support
		final var definitionCapabilities = new DefinitionCapabilities();
		definitionCapabilities.setLinkSupport(Boolean.TRUE);
		textDocumentClientCapabilities.setDefinition(definitionCapabilities);

		// TODO support type definition
		//final var typeDefinitionCapabilities = new TypeDefinitionCapabilities();
		//typeDefinitionCapabilities.setLinkSupport(Boolean.TRUE);
		//textDocumentClientCapabilities.setTypeDefinition(typeDefinitionCapabilities);

		// DocumentHighlight support
		textDocumentClientCapabilities.setDocumentHighlight(new DocumentHighlightCapabilities());

		// DocumentLink support
		textDocumentClientCapabilities.setDocumentLink(new DocumentLinkCapabilities());

		// TODO : support textDocument/documentSymbol
		/**  final var documentSymbol = new DocumentSymbolCapabilities();
		documentSymbol.setHierarchicalDocumentSymbolSupport(true);
		documentSymbol.setSymbolKind(new SymbolKindCapabilities(Arrays.asList(SymbolKind.Array,
				SymbolKind.Boolean, SymbolKind.Class, SymbolKind.Constant, SymbolKind.Constructor,
				SymbolKind.Enum, SymbolKind.EnumMember, SymbolKind.Event, SymbolKind.Field, SymbolKind.File,
				SymbolKind.Function, SymbolKind.Interface, SymbolKind.Key, SymbolKind.Method, SymbolKind.Module,
				SymbolKind.Namespace, SymbolKind.Null, SymbolKind.Number, SymbolKind.Object,
				SymbolKind.Operator, SymbolKind.Package, SymbolKind.Property, SymbolKind.String,
				SymbolKind.Struct, SymbolKind.TypeParameter, SymbolKind.Variable)));
		textDocumentClientCapabilities.setDocumentSymbol(documentSymbol);
		**/
		// TODO : support textDocument/foldingRange
		// textDocumentClientCapabilities.setFoldingRange(new FoldingRangeCapabilities());
		// TODO : support textDocument/formatting
		// textDocumentClientCapabilities.setFormatting(new FormattingCapabilities(Boolean.TRUE));

		// Hover support
		final var hoverCapabilities = new HoverCapabilities();
		hoverCapabilities.setContentFormat(Arrays.asList(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
		textDocumentClientCapabilities.setHover(hoverCapabilities);

		// TODO: support onTypeFormatting
		// textDocumentClientCapabilities.setOnTypeFormatting(null); // TODO
		// TODO : support textDocument/rangeFormatting
		// textDocumentClientCapabilities.setRangeFormatting(new RangeFormattingCapabilities());
		// TODO : support textDocument/references
		// textDocumentClientCapabilities.setReferences(new ReferencesCapabilities());
		// TODO : support textDocument/rename
		//final var renameCapabilities = new RenameCapabilities();
		//renameCapabilities.setPrepareSupport(true);
		//textDocumentClientCapabilities.setRename(renameCapabilities);
		// TODO : support textDocument/signatureHelp
		// textDocumentClientCapabilities.setSignatureHelp(new SignatureHelpCapabilities());

		// Synchronization support
		textDocumentClientCapabilities
				.setSynchronization(new SynchronizationCapabilities(Boolean.TRUE, Boolean.TRUE, Boolean.TRUE));

		// TODO
		// SelectionRangeCapabilities selectionRange = new SelectionRangeCapabilities();
		// textDocumentClientCapabilities.setSelectionRange(selectionRange);
		return textDocumentClientCapabilities;
	}

	public static @Nonnull WorkspaceClientCapabilities getWorkspaceClientCapabilities() {
		final var workspaceClientCapabilities = new WorkspaceClientCapabilities();
		workspaceClientCapabilities.setApplyEdit(Boolean.TRUE);
		// TODO
		// workspaceClientCapabilities.setConfiguration(Boolean.TRUE);
		workspaceClientCapabilities.setExecuteCommand(new ExecuteCommandCapabilities(Boolean.TRUE));
		// TODO
		// workspaceClientCapabilities.setSymbol(new SymbolCapabilities(Boolean.TRUE));
		workspaceClientCapabilities.setWorkspaceFolders(Boolean.TRUE);
		WorkspaceEditCapabilities editCapabilities = new WorkspaceEditCapabilities();
		editCapabilities.setDocumentChanges(Boolean.TRUE);
		// TODO
		// editCapabilities.setResourceOperations(Arrays.asList(ResourceOperationKind.Create,
		//		ResourceOperationKind.Delete, ResourceOperationKind.Rename));
		// TODO
		// editCapabilities.setFailureHandling(FailureHandlingKind.Undo);
		workspaceClientCapabilities.setWorkspaceEdit(editCapabilities);
		workspaceClientCapabilities.setDidChangeWatchedFiles(new DidChangeWatchedFilesCapabilities(Boolean.TRUE));
		return workspaceClientCapabilities;
	}

	public static WindowClientCapabilities getWindowClientCapabilities() {
		final var windowClientCapabilities = new WindowClientCapabilities();
		//windowClientCapabilities.setShowDocument(new ShowDocumentCapabilities(true));
		//windowClientCapabilities.setWorkDoneProgress(true);
		//windowClientCapabilities.setShowMessage(new WindowShowMessageRequestCapabilities());
		return windowClientCapabilities;
	}

}
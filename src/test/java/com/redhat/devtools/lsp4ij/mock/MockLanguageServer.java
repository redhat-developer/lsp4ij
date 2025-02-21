/*******************************************************************************
 * Copyright (c) 2016, 2022 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added support for delays
 *  Lucas Bullen (Red Hat Inc.) - Bug 508458 - Add support for codelens.
 *  Kris De Volder (Pivotal Inc.) - Provide test code access to Client proxy.
 *  Rubén Porras Campo (Avaloq Evolution AG) - Add support for willSaveWaitUntil.
 *  Joao Dinis Ferreira (Avaloq Group AG) - Add support for position-dependent mock document highlights
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.mock;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.NotebookDocumentService;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class is a copy/paste from <a href="https://github.com/eclipse/lsp4e/blob/master/org.eclipse.lsp4e.tests.mock/src/org/eclipse/lsp4e/tests/mock/MockLanguageServer.java">...</a>
 */
public final class MockLanguageServer implements LanguageServer {

	public static MockLanguageServer INSTANCE = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);

	/**
	 * This command will be reported on initialization to be supported for execution
	 * by the server
	 */
	public static String SUPPORTED_COMMAND_ID = "mock.command";

	private volatile MockTextDocumentService textDocumentService = new MockTextDocumentService(
			this::buildMaybeDelayedFuture);
	private final MockWorkspaceService workspaceService = new MockWorkspaceService(this::buildMaybeDelayedFuture);
	private final InitializeResult initializeResult = new InitializeResult();
	private volatile long delay = 0;
	private volatile boolean started;

	private final List<LanguageClient> remoteProxies = new ArrayList<>();

	private final List<CompletableFuture<?>> inFlight = new CopyOnWriteArrayList<>();

	public static void reset() {
		INSTANCE = new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
	}

	public static void reset(final Supplier<ServerCapabilities> serverConfigurer) {
		INSTANCE = new MockLanguageServer(serverConfigurer);
	}

	private MockLanguageServer(final Supplier<ServerCapabilities> serverConfigurer) {
		resetInitializeResult(serverConfigurer);
	}

	/**
	 * Starts the language server on stdin/stdout
	 *
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		Launcher<LanguageClient> l = LSPLauncher.createServerLauncher(MockLanguageServer.INSTANCE, System.in, System.out);
		Future<?> f = l.startListening();
		MockLanguageServer.INSTANCE.addRemoteProxy(l.getRemoteProxy());
		f.get();
	}

	public void waitBeforeTearDown() {
		inFlight.forEach(future -> {
			try {
				future.join();
			} catch (CancellationException | CompletionException e) {
				System.err.println("Error waiting for in flight requests prior to teardown: " + e.getMessage());
			}
		});
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.textDocumentService.addRemoteProxy(remoteProxy);
		this.remoteProxies.add(remoteProxy);
		this.started = true;
	}

	private void resetInitializeResult(final Supplier<ServerCapabilities> serverConfigurer) {
		initializeResult.setCapabilities(serverConfigurer.get());
	}

	public <U> CompletableFuture<U> buildMaybeDelayedFuture(U value) {
		if (delay > 0) {
			CompletableFuture<U> future = CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}).thenApply(v -> value);
			inFlight.add(future);
			return future;
		}
		return CompletableFuture.completedFuture(value);
	}

	public static ServerCapabilities defaultServerCapabilities() {
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
		CompletionOptions completionProvider = new CompletionOptions(false, null);
		capabilities.setCompletionProvider(completionProvider);
		capabilities.setHoverProvider(true);
		capabilities.setDefinitionProvider(true);
		capabilities.setTypeDefinitionProvider(Boolean.TRUE);
		capabilities.setReferencesProvider(true);
		capabilities.setDocumentFormattingProvider(true);
		capabilities.setSelectionRangeProvider(true);
		var codeActionOptions = new CodeActionOptions();
		codeActionOptions.setResolveProvider(Boolean.TRUE);
		capabilities.setCodeActionProvider(codeActionOptions);
		capabilities.setCodeLensProvider(new CodeLensOptions(true));
		capabilities.setDocumentLinkProvider(new DocumentLinkOptions());
		capabilities.setSignatureHelpProvider(new SignatureHelpOptions());
		capabilities.setDocumentHighlightProvider(Boolean.TRUE);
		capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Collections.singletonList(SUPPORTED_COMMAND_ID)));
		capabilities.setColorProvider(Boolean.TRUE);
		capabilities.setDocumentSymbolProvider(Boolean.TRUE);
		capabilities.setLinkedEditingRangeProvider(new LinkedEditingRangeRegistrationOptions());
		capabilities.setTypeHierarchyProvider(new TypeHierarchyRegistrationOptions());
		capabilities.setFoldingRangeProvider(new FoldingRangeProviderOptions());
		return capabilities;
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		return buildMaybeDelayedFuture(initializeResult);
	}

	@Override
	public MockTextDocumentService getTextDocumentService() {
		return textDocumentService;
	}

	public void setTextDocumentService(MockTextDocumentService custom) {
		textDocumentService = custom;
	}

	@Override
	public MockWorkspaceService getWorkspaceService() {
		return workspaceService;
	}

	public void setCompletionList(CompletionList completionList) {
		this.textDocumentService.setMockCompletionList(completionList);
	}

	public void setCompletionItem(CompletionItem completionItem) {
		this.textDocumentService.setMockCompletionItem(completionItem);
	}

	public void setSemanticTokens(SemanticTokens semanticTokens) {
		this.textDocumentService.setSemanticTokens(semanticTokens);
	}

	public void setHover(Hover hover) {
		this.textDocumentService.setMockHover(hover);
	}

	public void setCodeLens(List<CodeLens> codeLens) {
		this.textDocumentService.setMockCodeLenses(codeLens);
	}

	public void setDefinition(List<? extends Location> definitionLocations){
		this.textDocumentService.setMockDefinitionLocations(definitionLocations);
	}

	public void setDidOpenCallback(CompletableFuture<DidOpenTextDocumentParams> didOpenExpectation) {
		this.textDocumentService.setDidOpenCallback(didOpenExpectation);
	}

	public List<DidChangeTextDocumentParams> getDidChangeEvents() {
		return this.textDocumentService.getDidChangeEvents();
	}

	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.textDocumentService.setDidSaveCallback(didSaveExpectation);
	}

	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.textDocumentService.setDidCloseCallback(didCloseExpectation);
	}

	public void setFormattingTextEdits(List<? extends TextEdit> formattingTextEdits) {
		this.textDocumentService.setMockFormattingTextEdits(formattingTextEdits);
	}

	public void setSelectionRanges(List<SelectionRange> selectionRanges) {
		this.textDocumentService.setMockSelectionRanges(selectionRanges);
	}

	public void setDocumentHighlights(Map<Position, List<? extends DocumentHighlight>> documentHighlights) {
		this.textDocumentService.setDocumentHighlights(documentHighlights);
	}

	public void setLinkedEditingRanges(LinkedEditingRanges linkedEditingRanges) {
		this.textDocumentService.setLinkedEditingRanges(linkedEditingRanges);
	}

	public void setCompletionTriggerChars(Set<String> chars) {
		if (chars != null) {
			initializeResult.getCapabilities().getCompletionProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}

	public void setContextInformationTriggerChars(Set<String> chars) {
		if (chars != null) {
			initializeResult.getCapabilities().getSignatureHelpProvider().setTriggerCharacters(new ArrayList<>(chars));
		}
	}

	public void setWillSaveWaitUntil(List<TextEdit> edits) {
		TextDocumentSyncOptions textDocumentSyncOptions = new TextDocumentSyncOptions();
		textDocumentSyncOptions.setWillSaveWaitUntil(true);
		textDocumentSyncOptions.setSave(true);
		textDocumentSyncOptions.setChange(TextDocumentSyncKind.Full);
		initializeResult.getCapabilities().setTextDocumentSync(textDocumentSyncOptions);

		this.textDocumentService.setWillSaveWaitUntilCallback(edits);
	}

	public void setPrepareRenameProcessor(Function<PrepareRenameParams, Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRenameProcessor) {
		this.textDocumentService.setPrepareRenameProcessor(prepareRenameProcessor);
	}

	public void setRenameProcessor(Function<RenameParams, WorkspaceEdit> renameProcessor) {
		this.textDocumentService.setRenameProcessor(renameProcessor);
	}

	public InitializeResult getInitializeResult() {
		return initializeResult;
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		this.started = false;
		return buildMaybeDelayedFuture(Collections.emptySet());
	}

	@Override
	public void exit() {
	}

	public void setTimeToProceedQueries(long l) {
		this.delay = l;
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.textDocumentService.setDiagnostics(diagnostics);
	}

	public void setCodeActions(List<Either<Command, CodeAction>> codeActions) {
		this.textDocumentService.setCodeActions(codeActions);
	}

	public void setResolvedCodeAction(CodeAction codeAction) {
		this.textDocumentService.setResolvedCodeAction(codeAction);
	}

	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.textDocumentService.setSignatureHelp(signatureHelp);
	}

	public void setDocumentLinks(List<DocumentLink> documentLinks) {
		this.textDocumentService.setMockDocumentLinks(documentLinks);
	}

	public void setTypeDefinitions(List<LocationLink> locations) {
		this.textDocumentService.setMockTypeDefinitions(locations);
	}

	public boolean isRunning() {
		return this.started;
	}

	public List<LanguageClient> getRemoteProxies() {
		return remoteProxies;
	}

	public void setDocumentSymbols(DocumentSymbol documentSymbol) {
		this.textDocumentService.setDocumentSymbols(Collections.singletonList(documentSymbol));
	}

	public void setDocumentSymbols(DocumentSymbol... documentSymbols) {
		this.textDocumentService.setDocumentSymbols(Arrays.asList(documentSymbols));
	}

	@Override
	public NotebookDocumentService getNotebookDocumentService() {
		return new NotebookDocumentService() {
			@Override
			public void didSave(DidSaveNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didOpen(DidOpenNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didClose(DidCloseNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}

			@Override
			public void didChange(DidChangeNotebookDocumentParams params) {
				// TODO Auto-generated method stub
			}
		};
	}

	public void setFoldingRanges(List<FoldingRange> foldingRanges) {
		this.textDocumentService.setFoldingRanges(foldingRanges);
	}

	@Override
	public String toString() {
		return "MockLanguageServer [started=" + started + ", delay=" + delay + ", remoteProxies=" + remoteProxies.size()
				+ ", inFlight=" + inFlight.size() + "]";
	}

}
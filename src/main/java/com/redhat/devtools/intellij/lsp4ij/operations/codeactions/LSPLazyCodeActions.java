/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.codeactions;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.intellij.lsp4ij.CompletableFutures;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class returns 10 IJ {@link LSPLazyCodeActionIntentionAction} which does nothing. It loads the LSP code actions
 * for the given diagnostic only when user triggers the quick fixes for the diagnostic.
 *
 * @author Angelo ZERR
 */
public class LSPLazyCodeActions {

	public static final Either<Command, CodeAction> NO_CODEACTION_AT_INDEX = Either.forLeft(new Command());

	private static final int NB_LAZY_CODE_ACTIONS = 10;

	private static final long LSP_REQUEST_CODEACTION_TIMEOUT = 20L; // wait for 20ms to request the LSP textDocument/codeAction

	// The diagnostic
	private final Diagnostic diagnostic;

	// The virtual file
	private final VirtualFile file;

	// The language server which has reported the diagnostic
	private final LanguageServerWrapper languageServerWrapper;

	// List of lazy code actions
	private final List<LSPLazyCodeActionIntentionAction> codeActions;

	// LSP code actions request used to load code action for the diagnostic.
	private CompletableFuture<List<Either<Command, CodeAction>>> lspCodeActionRequest = null;
	private Boolean refreshValidation;

	public LSPLazyCodeActions(Diagnostic diagnostic, VirtualFile file, LanguageServerWrapper languageServerWrapper) {
		this.diagnostic = diagnostic;
		this.file = file;
		this.languageServerWrapper = languageServerWrapper;
		// Create 10 lazy IJ quick fixes which does nothing (IntentAction#isAvailable returns false)
		codeActions = new ArrayList<>(NB_LAZY_CODE_ACTIONS);
		for (int i = 0; i < NB_LAZY_CODE_ACTIONS; i++) {
			codeActions.add(new LSPLazyCodeActionIntentionAction(this, i));
		}
	}

	/**
	 * Returns the LSP CodeAction for the given index and null otherwise.
	 *
	 * @param index the code action index.
	 * @return the LSP CodeAction for the given index and null otherwise.
	 */
	public @Nullable Either<Command, CodeAction> getCodeActionAt(int index) {
		List<Either<Command, CodeAction>> codeActions = getOrLoadCodeActions();
		if (codeActions == null) {
			if (refreshValidation != null && refreshValidation) {
				// On the first TimeoutException, the IJ validator will be refreshed as soon as the code actions
				// will be loaded in order to refresh quick fixes by adding a new CompletableFuture with thenApply.
				lspCodeActionRequest.thenAccept(inn -> {
					ReadAction.compute(() -> {
						// Here The LSP request textDocument/codeAction takes some times
						if (codeActions != null) {
							// The code actions has been loaded, don't refresh the IJ validator.
							return null;
						}
						Project project = languageServerWrapper.getProject();
						PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
						DaemonCodeAnalyzer.getInstance(project).restart(psiFile);
						return null;
					});
				});
				// No need to add a new CompletableFuture which refreshes the IJ validator on the next TimeoutException
				refreshValidation = false;
			}
		}
		if (codeActions != null) {
			if (codeActions.size() > index) {
				// The LSP code actions are loaded and it matches the given index
				return codeActions.get(index);
			}
			return NO_CODEACTION_AT_INDEX;
		}
		return null;
	}

	@Nullable
	private List<Either<Command, CodeAction>> getOrLoadCodeActions() {
		if (lspCodeActionRequest == null) {
			// Create LSP textDocument/codeAction request
			lspCodeActionRequest = loadCodeActionsFor(diagnostic);
		}
		// Get the response of the LSP textDocument/codeAction request with TimeOut.
		List<Either<Command, CodeAction>> codeActions = null;
		try {
			codeActions = lspCodeActionRequest.get(LSP_REQUEST_CODEACTION_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			refreshValidation = true;
		} catch (Exception e) {
			// Do nothing
		}
		return codeActions;
	}

	/**
	 * load code actions for the given diagnostic.
	 *
	 * @param diagnostic the LSP diagnostic.
	 * @return list of Intellij {@link IntentionAction} which are used to create Intellij QuickFix.
	 */
	private CompletableFuture<List<Either<Command, CodeAction>>> loadCodeActionsFor(Diagnostic diagnostic) {
		return CompletableFutures
				.computeAsyncCompose(cancelChecker -> {
					return languageServerWrapper
							.getInitializedServer()
							.thenCompose(ls -> {
								// Language server is initialized here
								cancelChecker.checkCanceled();

								// Collect code action for the given file by using the language server
								CodeActionParams params = createCodeActionParams(diagnostic, file);
								return ls.getTextDocumentService()
										.codeAction(params)
										.thenApply(codeActions -> {
											// Code action are collected here
											cancelChecker.checkCanceled();
											if (codeActions == null) {
												return Collections.emptyList();
											}
											return codeActions;
										});
							});
				});
	}

	/**
	 * Create the LSP code action parameters for the given diagnostic and file.
	 *
	 * @param diagnostic the diagnostic.
	 * @param file       the file.
	 * @return the LSP code action parameters for the given diagnostic and file.
	 */
	private static CodeActionParams createCodeActionParams(Diagnostic diagnostic, VirtualFile file) {
		CodeActionParams params = new CodeActionParams();
		URI fileUri = LSPIJUtils.toUri(file);
		params.setTextDocument(LSPIJUtils.toTextDocumentIdentifier(fileUri));
		Range range = diagnostic.getRange();
		params.setRange(range);

		CodeActionContext context = new CodeActionContext(Arrays.asList(diagnostic));
		params.setContext(context);
		return params;
	}

	/**
	 * Returns the language server which has reported the diagnostic.
	 *
	 * @return the language server which has reported the diagnostic.
	 */
	public LanguageServerWrapper getLanguageServerWrapper() {
		return languageServerWrapper;
	}

	/**
	 * Returns the list of lazy code actions.
	 *
	 * @return the list of lazy code actions.
	 */
	public List<LSPLazyCodeActionIntentionAction> getCodeActions() {
		return codeActions;
	}

	/**
	 * Cancel if needed the LSP request textDocument/codeAction
	 */
	public void cancel() {
		if (lspCodeActionRequest != null && !lspCodeActionRequest.isDone()) {
			lspCodeActionRequest.cancel(true);
		}
	}
}

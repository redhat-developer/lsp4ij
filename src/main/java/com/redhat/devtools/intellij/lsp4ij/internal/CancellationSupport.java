/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.internal;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * LSP cancellation support hosts the list of LSP requests to cancel when a
 * process is canceled (ex: when completion is re-triggered, when hover is give
 * up, etc)
 *
 * @see <a href=
 *      "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest</a>
 */
public class CancellationSupport implements CancelChecker {

	private final List<CompletableFuture<?>> futuresToCancel;

	private boolean cancelled;

	public CancellationSupport() {
		this.futuresToCancel = new CopyOnWriteArrayList<>();
		this.cancelled = false;
	}

	public <T> CompletableFuture<T> execute(CompletableFuture<T> future) {
		if (cancelled) {
			future.cancel(true);
			throw new ProcessCanceledException();
		} else {
			this.futuresToCancel.add(future);
		}
		return future;
	}

	/**
	 * Cancel all LSP requests.
	 */
	public void cancel() {
		if (cancelled) {
			return;
		}
		this.cancelled = true;
		for (CompletableFuture<?> futureToCancel : futuresToCancel) {
			if (!futureToCancel.isDone()) {
				futureToCancel.cancel(true);
			}
		}
		futuresToCancel.clear();
	}

	@Override
	public void checkCanceled() {
		// When LSP requests are called (ex : 'textDocument/completion') the LSP
		// response
		// items are used to compose some UI item (ex : LSP CompletionItem are translate
		// to IJ LookupElement fo).
		// If the cancel occurs after the call of those LSP requests, the component
		// which uses the LSP responses
		// can call checkCanceled to stop the UI creation.
		if (cancelled) {
			throw new CancellationException();
		}
	}
}
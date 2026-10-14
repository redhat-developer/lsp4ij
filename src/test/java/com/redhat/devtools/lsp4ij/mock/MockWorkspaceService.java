/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Added ability to check for executed command
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.mock;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * This class is a copy/paste from <a href="https://github.com/eclipse/lsp4e/blob/master/org.eclipse.lsp4e.tests.mock/src/org/eclipse/lsp4e/tests/mock/MockWorkspaceService.java">...</a>
 */
public class MockWorkspaceService implements WorkspaceService {

	private Function<?, ?> _futureFactory;
	private CompletableFuture<ExecuteCommandParams> executedCommand = new CompletableFuture<>();
	private List<DidChangeWorkspaceFoldersParams> workspaceFoldersEvents = new ArrayList<>();
    private WorkspaceEdit willRename;
    private WorkspaceEdit willCreate;
    private WorkspaceEdit willDelete;
    private long willCreateDelayMs;
    private long willDeleteDelayMs;
    private long willRenameDelayMs;
    private Function<WorkspaceDiagnosticParams, CompletableFuture<WorkspaceDiagnosticReport>> diagnosticHandler;

    public void setDiagnosticHandler(Function<WorkspaceDiagnosticParams, CompletableFuture<WorkspaceDiagnosticReport>> handler) {
        this.diagnosticHandler = handler;
    }

    @Override
    public CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params) {
        return diagnosticHandler.apply(params);
    }


	public <U> MockWorkspaceService(Function<U, CompletableFuture<U>> futureFactory) {
		this._futureFactory = futureFactory;
	}

    public void setWillRename(WorkspaceEdit willRename) {
        this.willRename = willRename;
    }

    public void setWillCreate(WorkspaceEdit willCreate) {
        this.willCreate = willCreate;
    }

    public void setWillDelete(WorkspaceEdit willDelete) {
        this.willDelete = willDelete;
    }

    public void setWillCreateDelayMs(long delayMs) {
        this.willCreateDelayMs = delayMs;
    }

    public void setWillDeleteDelayMs(long delayMs) {
        this.willDeleteDelayMs = delayMs;
    }

    public void setWillRenameDelayMs(long delayMs) {
        this.willRenameDelayMs = delayMs;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willCreateFiles(CreateFilesParams params) {
        return delayedResult(willCreate, willCreateDelayMs);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willDeleteFiles(DeleteFilesParams params) {
        return delayedResult(willDelete, willDeleteDelayMs);
    }

    @Override
    public CompletableFuture<WorkspaceEdit> willRenameFiles(RenameFilesParams params) {
        return delayedResult(willRename, willRenameDelayMs);
    }

    private static CompletableFuture<WorkspaceEdit> delayedResult(WorkspaceEdit result, long delayMs) {
        if (delayMs <= 0) {
            return CompletableFuture.supplyAsync(() -> result);
        }
        CompletableFuture<WorkspaceEdit> future = new CompletableFuture<>();
        CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> future.complete(result));
        return future;
    }

    /**
	 * Use this method to get a future that will wait specified delay before returning
	 * value
	 * @param value the value that will be returned by the future
	 * @return a future that completes to value, after delay from {@link MockLanguageServer#delay}
	 */
	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>)this._futureFactory).apply(value);
	}

	@Override
	public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(
			WorkspaceSymbolParams params) {
		// No-op
		return null;
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		// No-op
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		// No-op
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
		workspaceFoldersEvents.add(params);
	}

	public List<DidChangeWorkspaceFoldersParams> getWorkspaceFoldersEvents() {
		return this.workspaceFoldersEvents;
	}

	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
		executedCommand.complete(params);
		return futureFactory(null);
	}

	public CompletableFuture<ExecuteCommandParams> getExecutedCommand() {
		return executedCommand;
	}
}
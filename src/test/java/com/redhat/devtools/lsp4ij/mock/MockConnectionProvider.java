/*******************************************************************************
 * Copyright (c) 2016, 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.mock;

import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * {@link StreamConnectionProvider} implementation used by the mock language server.
 */
public class MockConnectionProvider implements StreamConnectionProvider {

	private InputStream clientInputStream  ;
	private OutputStream clientOutputStream;
	private InputStream errorStream;
	private Future<Void> listener;
	private Collection<Closeable> streams = new ArrayList<>(4);

	private static ExecutorService testRunner = Executors.newCachedThreadPool();

	@Override
	public void start() throws CannotStartProcessException {
		Pipe serverOutputToClientInput = openPipe();
		Pipe clientOutputToServerInput = openPipe();
		errorStream = new ByteArrayInputStream("Error output on console".getBytes(StandardCharsets.UTF_8));

		InputStream serverInputStream = Channels.newInputStream(clientOutputToServerInput.source());
		OutputStream serverOutputStream = Channels.newOutputStream(serverOutputToClientInput.sink());
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(MockLanguageServer.INSTANCE, serverInputStream,
				serverOutputStream, testRunner, Function.identity());
		clientInputStream = Channels.newInputStream(serverOutputToClientInput.source());
		clientOutputStream = Channels.newOutputStream(clientOutputToServerInput.sink());
		listener = launcher.startListening();
		MockLanguageServer.INSTANCE.addRemoteProxy(launcher.getRemoteProxy());

		// Store the output streams so we can close them to clean up. The corresponding input
		// streams should automatically receive an EOF and close.
		streams.add(clientOutputStream);
		streams.add(serverOutputStream);
		streams.add(errorStream);
	}

	private static Pipe openPipe() throws CannotStartProcessException {
        try {
            return Pipe.open();
        } catch (IOException e) {
            throw new CannotStartProcessException(e);
        }
    }

	@Override
	public InputStream getInputStream() {
		return clientInputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return clientOutputStream;
	}

	@Override
	public InputStream getErrorStream() {
		return errorStream;
	}

	@Override
	public void stop() {
		streams.forEach(t -> {
			try {
				t.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		streams.clear();
		if (listener != null) {
			listener.cancel(true);
		}
		listener = null;
	}

	public static final Collection<Message> cancellations = new ArrayList<>();

	@Override
	public void handleMessage(Message message, LanguageServer languageServer, @Nullable URI rootURI) {
		if (message.toString().contains("cancelRequest")) {
			cancellations.add(message);
		}
		StreamConnectionProvider.super.handleMessage(message, languageServer, rootURI);
	}
}
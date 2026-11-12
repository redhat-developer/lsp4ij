/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.redhat.devtools.lsp4ij.internal.LoopbackAddresses;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Objects;

/**
 * This class is a copy of
 * <a href="https://github.com/eclipse-lsp4e/lsp4e/blob/main/org.eclipse.lsp4e.debug/src/org/eclipse/lsp4e/debug/debugmodel/TransportStreams.java">TransportStreams.java</a>
 */
public abstract class TransportStreams {

	public InputStream in = null;
	public OutputStream out = null;

	public void close() {
		try {
			in.close();
		} catch (IOException e1) {
			// ignore inner resource exception
		}
		try {
			out.close();
		} catch (IOException e1) {
			// ignore inner resource exception
		}
	}

	public TransportStreams withTrace() {
		return new DefaultTransportStreams(new TraceInputStream(in, System.out), new TraceOutputStream(out, System.out)) {
			@Override
			public void close() {
				TransportStreams.this.close();
			}
		};
	}

	public static class DefaultTransportStreams extends TransportStreams {
		public DefaultTransportStreams(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
		}
	}

	public static class SocketTransportStreams extends TransportStreams {

		/**
		 * How long the connection to an address, which another address follows, waits, as the wait for the socket of the
		 * DAP server waits for each address.
		 */
		static final int CONNECT_TIMEOUT_MILLIS = 500;

		private final Socket socket;

		/**
		 * Connects to the given port of the host, trying each of the host's addresses in turn. Without a host, or with a
		 * blank one, it tries both loopback addresses, 127.0.0.1 and ::1, so that it reaches a DAP server which listens
		 * on localhost, whichever of the two the server got.
		 */
		public SocketTransportStreams(@Nullable String host, int port) {
			try {
				this.socket = connect(LoopbackAddresses.of(host), port);
				in = socket.getInputStream();
				out = socket.getOutputStream();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} 
		}

		/**
		 * Connects to the first of the given addresses which takes the connection. Each address but the last waits
		 * {@link #CONNECT_TIMEOUT_MILLIS}, so that one which drops the connection doesn't hold up the next ones; the last
		 * one, as a single address, waits as long as the system does. When none takes the connection, it fails as the
		 * first address did, as with "Connection refused", and the failures of the others are suppressed in it.
		 */
		private static @NotNull Socket connect(@NotNull List<InetAddress> addresses, int port) throws IOException {
			IOException failure = null;
			for (int i = 0; i < addresses.size(); i++) {
				var socket = new Socket();
				try {
					socket.connect(new InetSocketAddress(addresses.get(i), port),
							i < addresses.size() - 1 ? CONNECT_TIMEOUT_MILLIS : 0);
					return socket;
				} catch (IOException e) {
					try {
						socket.close();
					} catch (IOException ignored) {
						// The failure of the connection is the one to report
					}
					if (failure == null) {
						failure = e;
					} else {
						failure.addSuppressed(e);
					}
				}
			}
			throw Objects.requireNonNull(failure, "no address to connect to");
		}

		@Override
		public void close() {
			super.close();
			try {
				socket.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} 
		}
	}
}
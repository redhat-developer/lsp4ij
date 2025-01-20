/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

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
		private final Socket socket;

		public SocketTransportStreams(String host, int port) {
			try {
				this.socket = new Socket(host, port);
				in = socket.getInputStream();
				out = socket.getOutputStream();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			} 
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
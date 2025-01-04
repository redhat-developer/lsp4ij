/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class is a copy of
 * <a href="https://github.com/eclipse-lsp4e/lsp4e/blob/main/org.eclipse.lsp4e.debug/src/org/eclipse/lsp4e/debug/debugmodel/TraceInputStream.java">TraceInputStream.java</a>
 */
public class TraceInputStream extends FilterInputStream {
	private final OutputStream trace;

	public TraceInputStream(InputStream in, OutputStream trace) {
		super(in);
		this.trace = trace;
	}

	@Override
	public int read() throws IOException {
		int b = in.read();
		trace.write(b);
		trace.flush();
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int n = in.read(b, off, len);
		trace.write(b, off, n);
		trace.flush();
		return n;
	}
}
/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This class is a copy of
 * <a href="https://github.com/eclipse-lsp4e/lsp4e/blob/main/org.eclipse.lsp4e.debug/src/org/eclipse/lsp4e/debug/debugmodel/TraceOutputStream.java">TraceOutputStream.java</a>
 */
public class TraceOutputStream extends FilterOutputStream {

	private final OutputStream trace;

	public TraceOutputStream(OutputStream out, OutputStream trace) {
		super(out);
		this.trace = trace;
	}

	@Override
	public void write(int b) throws IOException {
		trace.write(b);
		trace.flush();
		out.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		trace.write(b, off, len);
		trace.flush();
		out.write(b, off, len);
	}
}
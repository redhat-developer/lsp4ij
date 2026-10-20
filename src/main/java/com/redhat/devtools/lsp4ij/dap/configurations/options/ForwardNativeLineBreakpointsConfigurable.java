/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.options;

/**
 * Configurable for whether a language's native line breakpoints on DAP-mapped files are forwarded to
 * the debug adapter while a DAP session runs (see {@code DAPForwardedLineBreakpointHandler}).
 */
public interface ForwardNativeLineBreakpointsConfigurable {

    boolean isForwardNativeLineBreakpoints();

    void setForwardNativeLineBreakpoints(boolean forwardNativeLineBreakpoints);

}

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

/**
 * Whether a thread of a DAP session runs, is stopped, or has exited (see {@link DAPThreadRunStates}).
 */
public enum DAPThreadRunState {
    RUNNING,
    STOPPED,
    EXITED
}

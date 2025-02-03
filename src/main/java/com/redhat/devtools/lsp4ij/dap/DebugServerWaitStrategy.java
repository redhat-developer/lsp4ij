/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import org.jetbrains.annotations.NotNull;

/**
 * Debug server wait strategy.
 */
public enum DebugServerWaitStrategy {

    /** Waits for a fixed timeout before assuming the server is ready. */
    TIMEOUT,

    /** Waits for a specific trace/log message indicating server readiness. */
    TRACE;

    /**
     * Retrieves the corresponding {@link DebugServerWaitStrategy} from a string value.
     *
     * @param value the string representation of the strategy (case-insensitive).
     * @return the matching {@link DebugServerWaitStrategy}, or {@link #TIMEOUT} if the input is invalid.
     */
    @NotNull
    public static DebugServerWaitStrategy get(String value) {
        try {
            return DebugServerWaitStrategy.valueOf(value.toUpperCase());
        }
        catch(Exception e) {
            return DebugServerWaitStrategy.TIMEOUT;
        }
    }
}

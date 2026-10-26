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
package com.redhat.devtools.lsp4ij.dap.client.variables;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The format in which a debug session asks its debug adapter for values: hexadecimal numbers when the user chose
 * them in the Variables view, if the debug adapter supports value formatting (supportsValueFormattingOptions).
 */
public class DAPValueFormat {

    private volatile boolean hexadecimal;

    /**
     * Returns true if the user chose to see values as hexadecimal numbers, and false otherwise.
     *
     * @return true if the user chose to see values as hexadecimal numbers, and false otherwise.
     */
    public boolean isHexadecimal() {
        return hexadecimal;
    }

    /**
     * Sets whether values are shown as hexadecimal numbers.
     *
     * @param hexadecimal true to show values as hexadecimal numbers.
     */
    public void setHexadecimal(boolean hexadecimal) {
        this.hexadecimal = hexadecimal;
    }

    /**
     * Returns true if the debug adapter with the given capabilities formats values, and false otherwise.
     *
     * @param capabilities the debug adapter capabilities.
     * @return true if the debug adapter with the given capabilities formats values, and false otherwise.
     */
    public static boolean isSupported(@NotNull Capabilities capabilities) {
        return Boolean.TRUE.equals(capabilities.getSupportsValueFormattingOptions());
    }

    /**
     * Returns the DAP value format for a debug adapter with the given capabilities, and null if values are asked
     * for in the debug adapter's own format.
     *
     * @param capabilities the debug adapter capabilities.
     * @return the DAP value format, and null if values are asked for in the debug adapter's own format.
     */
    public @Nullable ValueFormat toValueFormat(@NotNull Capabilities capabilities) {
        if (!hexadecimal || !isSupported(capabilities)) {
            return null;
        }
        ValueFormat format = new ValueFormat();
        format.setHex(true);
        return format;
    }
}

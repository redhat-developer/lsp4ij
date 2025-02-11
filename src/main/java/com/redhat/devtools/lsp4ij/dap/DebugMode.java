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
 * The debug mode ("launch", "attach").
 */
public enum DebugMode {

    LAUNCH,
    ATTACH;

    @NotNull
    public static DebugMode get(String value) {
        try {
            return DebugMode.valueOf(value.toUpperCase());
        }
        catch(Exception e) {
            return DebugMode.LAUNCH;
        }
    }
}

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
package com.redhat.devtools.lsp4ij.mcp.settings;

/**
 * MCP server trace level used to show the MCP requests/responses in the MCP console.
 */
public enum MCPTrace {
    off,      // don't show any messages
    messages, // show only message without detail
    verbose;  // show message with detail

    public static MCPTrace getDefaultValue() {
        return off;
    }

    public static MCPTrace get(String value) {
        try {
            return MCPTrace.valueOf(value);
        } catch (Exception e) {
            return getDefaultValue();
        }
    }
}

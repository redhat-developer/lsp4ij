/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.settings;

/**
 * Language server trace level.
 */
public enum ServerTrace {

    off,
    messages,
    verbose;

    public static ServerTrace getServerTrace(String serverTrace) {
        if (serverTrace == null || serverTrace.isEmpty()) {
            return ServerTrace.off;
        }
        try {
            return ServerTrace.valueOf(serverTrace);
        }
        catch(Exception e) {
            return ServerTrace.off;
        }
    }
}

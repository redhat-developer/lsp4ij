/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Kris De Volder - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import org.jetbrains.annotations.NotNull;

/**
 * Exception raised when trying to use a server which was expected to
 * be kept in the {@link com.redhat.devtools.lsp4ij.ServerStatus}.started
 * state by an active {@link Lease} but has nevertheless
 * been terminated.
 */
public class ServerWasStoppedException extends LanguageServerException {
    public ServerWasStoppedException(@NotNull String msg) {
        super(msg);
    }
}

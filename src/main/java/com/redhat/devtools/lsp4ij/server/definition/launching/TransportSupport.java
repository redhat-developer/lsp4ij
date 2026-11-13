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
package com.redhat.devtools.lsp4ij.server.definition.launching;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A transport which a language server supports, as its template lists it: the mode, and the argument of the command
 * for it, such as {@code --stdio}, or {@code --socket=${port}} for a socket.
 *
 * @param mode     the mode.
 * @param argument the argument of the command for this mode, or null if the template names none.
 */
public record TransportSupport(@NotNull UserDefinedTransport.Mode mode, @Nullable String argument) {
}

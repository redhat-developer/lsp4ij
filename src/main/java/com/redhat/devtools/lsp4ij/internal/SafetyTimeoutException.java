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
package com.redhat.devtools.lsp4ij.internal;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * Exception thrown when a safety timeout is triggered to prevent deadlocks or UI freezes.
 *
 * <p>This exception is thrown by {@link CompletableFutures#waitUntilDone} when it detects
 * that waiting for a future in a dangerous context (WriteAction, ReadAction, or EDT) has
 * exceeded the safety timeout limit.</p>
 *
 * <p><b>Dangerous contexts and their timeouts:</b></p>
 * <ul>
 *   <li><b>WriteAction</b>: 500ms max - prevents guaranteed deadlock (LSP requests need ReadAction)</li>
 *   <li><b>ReadAction</b>: 2000ms max - prevents potential deadlock (LSP might need WriteAction)</li>
 *   <li><b>EDT</b>: 1000ms max - prevents visible UI freeze</li>
 * </ul>
 *
 * <p><b>Handling this exception:</b></p>
 * <pre>{@code
 * try {
 *     waitUntilDone(future, file);
 * } catch (SafetyTimeoutException e) {
 *     // Safety timeout triggered - use async fallback
 *     registerCallbackForLater(future);
 *     return null;
 * }
 * }</pre>
 *
 * <p>This exception extends {@link CancellationException} so existing code that catches
 * {@code CancellationException} will continue to work, while new code can specifically
 * catch {@code SafetyTimeoutException} to distinguish safety timeouts from other cancellations.</p>
 *
 * @see CompletableFutures#waitUntilDone
 */
public class SafetyTimeoutException extends CancellationException {

    /**
     * Creates a new safety timeout exception with the specified detail message.
     *
     * @param message the detail message, typically including the timeout duration and context
     *                (e.g., "Safety timeout after 2000ms in ReadAction")
     */
    public SafetyTimeoutException(@NotNull String message) {
        super(message);
    }
}

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
package com.redhat.devtools.intellij.lsp4ij.client;


/**
 * Exception thrown when the restart of non blocking read action for a given service is reached.
 */
public class ExecutionAttemptLimitReachedException extends RuntimeException {
    public ExecutionAttemptLimitReachedException(String executionName, int limit, Throwable ex) {
        super("Execution attempt limit (" + limit + ") reached to execute '" + executionName + ".", ex);
    }
}

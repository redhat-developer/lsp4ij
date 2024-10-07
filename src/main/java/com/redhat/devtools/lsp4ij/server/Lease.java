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

import com.intellij.openapi.Disposable;

/**
 * A 'Lease' represents a lease on an item that depends on the 'liveness' of a LanguageServer instance.
 * <p>
 * This represents a 'claim' on the language server expressing an intent of some client-side operations
 * to continue using the server for some time.
 * <p>
 * See docs on {@link Disposable} to learn how to release a claim (by disposing the lease).
 */
public interface Lease<T> extends Disposable {

    /**
     * Get a reference to leased item for immediate use. The reference is
     * not meant to be stored, instead call this method every time you need
     * to use the language server or need to check whether it is still alive.
     *
     * @throws LanguageServerException if the server is no longer alive.
     * @throws IllegalStateException if trying to use an already disposed lease.
     */
    T get() throws LanguageServerException, IllegalStateException;
}

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

import java.util.function.Function;

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
     * @throws ServerWasStoppedException if the server is no longer alive.
     * @throws IllegalStateException if trying to use an already disposed lease.
     */
    T get() throws ServerWasStoppedException, IllegalStateException;

    /**
     * Transforms the value in this lease by applying a function to it.
     * <p>
     * The result of applying the function is not cached, instead each time the lease's
     * {@code get()} method is called the function will be called again.
     * <p>
     * The returned lease doesn't create a new claim on the language server's lifetime but
     * rather shares the same claim as the original lease. Thus disposing either the
     * original or transformed lease will implicitly dispose both of them at the same time.
     * <p>
     * A typical use of this method might be to access the languageserver associated with
     * the lease and cast it to a more specific type. For example:
     * <pre>{@code
     *     Lease<MyLanguageServer> serverLease = serverItem
     *          .keepAlive()
     *          .transform(si -> (MyLanguageServer)si.getServer());
     * }</pre>
     *
     * @return A Lease that shares the same liveness claim as the original lease.
     */
    default <R> Lease<R> transform(Function<T, R> f) {
        var self = this;
        return new Lease<>() {
            @Override
            public R get() throws ServerWasStoppedException, IllegalStateException {
                return f.apply(self.get());
            }

            @Override
            public void dispose() {
                self.dispose();
            }
        };
    }
}

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
package com.redhat.devtools.lsp4ij.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * The addresses to connect to for a host, or without a host, the loopback addresses.
 * <p>
 * A process which listens on {@code localhost} listens on {@code 127.0.0.1} or on {@code ::1}, whichever the system
 * resolves {@code localhost} to first, as Node.js does. Connecting to both reaches it either way.
 */
public final class LoopbackAddresses {

    private static final List<InetAddress> LOOPBACK_ADDRESSES = loopbackAddresses();

    private LoopbackAddresses() {
    }

    /**
     * Returns the loopback address of the JVM, {@link InetAddress#getLoopbackAddress()}, and then the other one,
     * {@code ::1} or {@code 127.0.0.1}. Without IPv6, the loopback address of the JVM only.
     */
    public static @NotNull List<InetAddress> get() {
        return LOOPBACK_ADDRESSES;
    }

    /**
     * Returns the addresses of the given host, in the order in which the system resolves it, or without a host, or with
     * a blank one, the loopback addresses.
     */
    public static @NotNull List<InetAddress> of(@Nullable String host) throws UnknownHostException {
        return host != null && !host.isBlank() ? List.of(InetAddress.getAllByName(host)) : LOOPBACK_ADDRESSES;
    }

    private static @NotNull List<InetAddress> loopbackAddresses() {
        InetAddress jvm = InetAddress.getLoopbackAddress();
        try {
            // An IP address is never looked up
            return List.of(jvm, InetAddress.getByName(jvm instanceof Inet6Address ? "127.0.0.1" : "::1"));
        } catch (UnknownHostException e) {
            return List.of(jvm);
        }
    }
}

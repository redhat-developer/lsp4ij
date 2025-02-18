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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.redhat.devtools.lsp4ij.dap.configurations.extractors.NetworkAddressExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for server readiness.
 * This class holds the configuration options related to server readiness,
 * such as the pattern for matching the server ready message and the connection timeout.
 */
public class ServerReadyConfig {

    // Used by "launch" request
    private final @Nullable NetworkAddressExtractor debugServerReadyPattern;
    private final @Nullable Integer connectTimeout;

    // Used by "attach" request
    private final @Nullable String address;
    private final @Nullable Integer port;

    /**
     * Creates a new ServerReadyConfig using the provided pattern.
     *
     * @param pattern the pattern to be used for extracting the server ready message.
     */
    public ServerReadyConfig(@NotNull String pattern) {
        this(new NetworkAddressExtractor(pattern));
    }

    /**
     * Creates a new ServerReadyConfig using the provided NetworkAddressExtractor.
     *
     * @param debugServerReadyPattern the NetworkAddressExtractor to extract the server ready message pattern.
     */
    public ServerReadyConfig(@NotNull NetworkAddressExtractor debugServerReadyPattern) {
        this(debugServerReadyPattern, null, null, null);
    }

    /**
     * Creates a new ServerReadyConfig using the provided connection timeout.
     *
     * @param connectTimeout the connection timeout in milliseconds.
     */
    public ServerReadyConfig(int connectTimeout) {
        this(null, connectTimeout, null, null);
    }

    public ServerReadyConfig(@Nullable String address, int port) {
        this(null, null, address, port);
    }

    /**
     * Creates a new ServerReadyConfig with both the server ready pattern and connection timeout.
     *
     * @param debugServerReadyPattern the NetworkAddressExtractor to extract the server ready message pattern.
     * @param connectTimeout the connection timeout in milliseconds.
     */
    private ServerReadyConfig(@Nullable NetworkAddressExtractor debugServerReadyPattern,
                             @Nullable Integer connectTimeout,
                              @Nullable String address,
                              @Nullable Integer port) {
        this.debugServerReadyPattern = debugServerReadyPattern;
        this.connectTimeout = connectTimeout;
        this.address = address;
        this.port = port;
    }

    /**
     * Gets the NetworkAddressExtractor used to extract the server ready message pattern.
     *
     * @return the NetworkAddressExtractor, or null if not provided.
     */
    public @Nullable NetworkAddressExtractor getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    /**
     * Gets the connection timeout value.
     *
     * @return the connection timeout in milliseconds, or null if not provided.
     */
    public @Nullable Integer getConnectTimeout() {
        return connectTimeout;
    }

    public @Nullable String getAddress() {
        return address;
    }

    public @Nullable Integer getPort() {
        return port;
    }
}

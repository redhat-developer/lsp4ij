/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations.extractors;

import org.jetbrains.annotations.Nullable;

/**
 * Extractor result.
 *
 * @param matches true if the result matches the input from {@link NetworkAddressExtractor#extract(String)}
 * @param address the address value retrieved from ${address} and null otherwise.
 * @param port    the address value retrieved from ${port} and null otherwise.
 */
public record ExtractorResult(boolean matches,
                              @Nullable String address,
                              @Nullable String port) {
}

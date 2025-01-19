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

import org.jetbrains.annotations.Nullable;

/**
 * Server ready configuration.
 *
 * @param waitForTrace
 * @param waitForTimeout
 */
public record ServerReadyConfig(@Nullable String waitForTrace, @Nullable Integer waitForTimeout) {

}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client;

/**
 * Execute LSP feature status
 */
public enum ExecuteLSPFeatureStatus {

    NOW, // The LSP feature can be executed now for a given file.
    AFTER_INDEXING, // The LSP feature can be executed for a given file after the indexing project
    NOT // The LSP feature cannot be executed for a given file.
}

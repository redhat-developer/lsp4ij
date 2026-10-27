/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.features.workspaceFolder;

/**
 * Type of roots to use for workspace folders.
 */
public enum RootType {
    /**
     * Use project base directories.
     */
    PROJECT_BASE,

    /**
     * Use module source roots.
     */
    SOURCE_ROOTS,

    /**
     * Discover folders dynamically using marker files.
     */
    MARKERS,

    /**
     * No workspace folders.
     */
    NONE
}

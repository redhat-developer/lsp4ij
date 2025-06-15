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
package com.redhat.devtools.lsp4ij.settings.contributors;

/**
 * Contributor interface for providing the default content
 * of the "initializationOptions" field used during
 * the Language Server Protocol initialization.
 * <p>
 * According to the LSP specification, the "initializationOptions" field
 * is an optional property that can contain arbitrary data
 * (usually JSON) sent from the client to the server
 * to customize or configure the server during initialization.
 * <p>
 * Implementations should return a JSON string representing
 * the initialization options to be sent to the language server.
 */
public interface ServerInitializationOptionsContributor {

    /**
     * Returns the default content to include in the
     * server's initializationOptions during language server
     * initialization.
     * <p>
     * This should be a JSON string representing configuration
     * or options that customize the language server behavior.
     *
     * @return a JSON string representing the initialization options content
     */
    String getDefaultInitializationOptionsContent();

}

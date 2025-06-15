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
 * of the "experimental" field in the Language Server Protocol
 * initialization options.
 * <p>
 * According to the LSP specification, the "experimental" field
 * is an optional property which can hold arbitrary data
 * (of any JSON type) that represents experimental client capabilities
 * or server options not yet standardized.
 * <p>
 * Implementations should return a JSON string representing
 * the experimental capabilities or configuration data
 * to be sent during the language server initialization.
 */
public interface ServerExperimentalContributor {

    /**
     * Returns the default experimental content to include
     * in the server initialization options.
     * <p>
     * This should be a JSON string representing any experimental
     * capabilities or configuration as defined by the client-server agreement.
     *
     * @return a JSON string representing the experimental data
     */
    String getDefaultExperimentalContent();

}

/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

/**
 * Semantic tokens inspector listener.
 */
public interface SemanticTokensInspectorListener {

    /**
     * Callback called when a new semantic tokens is consumes for a given file.
     *
     * @param data the semantic inspector data.
     */
    void notify(SemanticTokensInspectorData data);
}

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
package com.redhat.devtools.lsp4ij.features.codeAction;

import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Code action provider API. Class which implements this API load in background
 * code actions and provide teh capability to get code action from a given index.
 */
public interface LSPLazyCodeActionProvider {

    /**
     * Returns a code action at the given index and false otherwise.
     * @param index the index.
     * @return a code action at the given index and false otherwise.
     */
    Either<CodeActionData, Boolean /* false when code action is not already loaded*/> getCodeActionAt(int index);

    /**
     * Clear code actions cache.
     */
    void clear();
}

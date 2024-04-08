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

import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;

/**
 * LSP document link data.
 *
 * @param codeAction               the LSP code action.
 * @param languageServer         the language server which has created the code action.
 */
public record CodeActionData(

        @NotNull Either<Command, CodeAction> codeAction,
        @NotNull LanguageServerWrapper languageServer) {
}
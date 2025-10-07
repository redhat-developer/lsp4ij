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
package com.redhat.devtools.lsp4ij.features.formatting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.FormattingOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP formatting parameters.
 *
 * @param textRange             the text range and null otherwise.
 * @param document              the document.
 * @param formattingServer
 * @param formattingOptions
 */
public record LSPFormattingParams(@Nullable TextRange textRange,
                                  @NotNull Document document,
                                  @NotNull LanguageServerItem formattingServer,
                                  @NotNull FormattingOptions formattingOptions) {

}

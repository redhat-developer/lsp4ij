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
package com.redhat.devtools.lsp4ij.features.rename;

import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.jetbrains.annotations.NotNull;

/**
 * Prepare rename response.
 *
 * @param range          the range.
 * @param placeholder    the placeholder.
 * @param languageServer the language server which has been used to compute this prepare rename response.
 */
record PrepareRenameResultData(@NotNull TextRange range,
                               @NotNull String placeholder,
                               @NotNull LanguageServerItem languageServer) {

}

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
package com.redhat.devtools.lsp4ij.features.color;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.ColorInformation;
import org.jetbrains.annotations.NotNull;

/**
 * LSP Color data.
 *
 * @param color               the LSP color information
 * @param languageServer         the language server which has created the color information.
 */
record ColorData(@NotNull ColorInformation color,
                 @NotNull LanguageServerItem languageServer) {
}

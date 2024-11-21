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
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.jetbrains.annotations.NotNull;

/**
 * Type hierarchy item Data
 *
 * @param typeHierarchyItem               the LSP type hierarchy item
 * @param languageServer         the language server which has created the type hierarchy item.
 */
record TypeHierarchyItemData(@NotNull TypeHierarchyItem typeHierarchyItem,
                             @NotNull LanguageServerItem languageServer) {

}

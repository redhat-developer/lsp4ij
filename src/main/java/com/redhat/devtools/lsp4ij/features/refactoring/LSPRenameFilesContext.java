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
package com.redhat.devtools.lsp4ij.features.refactoring;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.RenameFilesParams;

import java.util.List;

/**
 * LSP rename files context.
 *
 * @param params
 * @param servers
 * @param file
 */
public record LSPRenameFilesContext(RenameFilesParams params, List<LanguageServerItem> servers, PsiFile file) {
}

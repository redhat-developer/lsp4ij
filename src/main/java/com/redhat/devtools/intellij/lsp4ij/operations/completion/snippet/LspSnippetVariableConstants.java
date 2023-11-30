/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet;

/**
 * LSP variables.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public class LspSnippetVariableConstants {

    private LspSnippetVariableConstants() {

    }

    /**
     * The currently selected text or the empty string
     */
    public static final String TM_SELECTED_TEXT = "TM_SELECTED_TEXT"; //$NON-NLS-1$

    /**
     * The contents of the current line
     */
    public static final String TM_CURRENT_LINE = "TM_CURRENT_LINE"; //$NON-NLS-1$

    /**
     * The contents of the word under cursor or the empty string
     */
    public static final String TM_CURRENT_WORD = "TM_CURRENT_WORD"; //$NON-NLS-1$
    /**
     * The zero-index based line number
     */

    public static final String TM_LINE_INDEX = "TM_LINE_INDEX"; //$NON-NLS-1$
    /**
     * The one-index based line number
     */
    public static final String TM_LINE_NUMBER = "TM_LINE_NUMBER"; //$NON-NLS-1$

    /**
     * The filename of the current document
     */
    public static final String TM_FILENAME = "TM_FILENAME"; //$NON-NLS-1$

    /**
     * The filename of the current document without its extensions
     */
    public static final String TM_FILENAME_BASE = "TM_FILENAME_BASE"; //$NON-NLS-1$

    /**
     * The directory of the current document
     */
    public static final String TM_DIRECTORY = "TM_DIRECTORY"; //$NON-NLS-1$

    /**
     * The full file path of the current document
     */
    public static final String TM_FILEPATH = "TM_FILEPATH"; //$NON-NLS-1$
}

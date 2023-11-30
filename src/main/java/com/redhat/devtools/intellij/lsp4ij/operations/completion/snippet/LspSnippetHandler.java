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

import java.util.List;

/**
 * LSP snippet handler (aka SAXHandler).
 *
 * @author Angelo ZERR
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public interface LspSnippetHandler {

    /**
     * On start snippet.
     */
    void startSnippet();

    /**
     * On end snippet.
     */
    void endSnippet();

    /**
     * On text block.
     *
     * @param text the text block.
     */
    void text(String text);

    /**
     * On tabstop (ex: $1}.
     *
     * @param index the tabstop index (ex:1)
     */
    void tabstop(int index);

    /**
     * On choice (ex : ${1|one,two,three|}).
     *
     * @param index   the choice index (ex:1)
     * @param choices the choices list (ex: [one,two,three])
     */
    void choice(int index, List<String> choices);

    /**
     * On choice (ex : ${two|one,two,three|}).
     *
     * @param name    the choice name (ex:two)
     * @param choices the choices list (ex: [one,two,three])
     */
    void choice(String name, List<String> choices);

    /**
     * On start placeholder (ex : {1:name}).
     *
     * @param index the placeholder index (ex:1)
     * @param name  the placeholder name (ex:name)
     * @param level the placeholder level (1 for root and other for nested placeholder)
     */
    void startPlaceholder(int index, String name, int level);

    /**
     * On end place holder.
     *
     * @param level the placeholder level (1 for root and other for nested placeholder)
     */
    void endPlaceholder(int level);

    /**
     * On variable (ex : ${name}
     *
     * @param name the variable name (ex:name)
     */
    void variable(String name);

}

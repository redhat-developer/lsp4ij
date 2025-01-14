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

package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetIndentOptions;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetParser;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Intellij {@link Template} factory to create the proper template structure (text segment, variables, etc) according the LSP snippet content.
 *
 * @author Angelo ZERR
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax>https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#snippet_syntax</a>
 */
public class SnippetTemplateFactory {

    /**
     * Returns an Intellij Template instanced loaded from the given LSP snippet content.
     *
     * @param snippetContent                      the LSP snippet content
     * @param project                             the project.
     * @param variableResolver                    the LSP variable resolvers (ex to resolve ${TM_SELECTED_TEXT}).
     * @param useTemplateForInvocationOnlySnippet whether or not a template should be used for an invocation-only snippet
     * @return an Intellij Template instanced loaded from the given LSP snippet content.
     */
    public static @NotNull Template createTemplate(@NotNull String snippetContent,
                                                   @NotNull Project project,
                                                   @NotNull Function<String, String> variableResolver,
                                                   LspSnippetIndentOptions indentOptions,
                                                   boolean useTemplateForInvocationOnlySnippet) {
        Template template = TemplateManager.getInstance(project).createTemplate("", "");
        template.setInline(true);
        LspSnippetParser parser = new LspSnippetParser(new SnippetTemplateLoader(
                template,
                variableResolver,
                indentOptions,
                useTemplateForInvocationOnlySnippet
        ));
        parser.parse(snippetContent);
        return template;
    }
}

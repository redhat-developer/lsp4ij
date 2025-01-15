/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
import com.redhat.devtools.lsp4ij.fixtures.LSPCompletionFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests behavior of {@link SnippetTemplateLoader} via {@link SnippetTemplateFactory}.
 */
public class SnippetTemplateLoaderTest extends LSPCompletionFixtureTestCase {

    private static final String END_PLACEHOLDER = "<end>";

    public SnippetTemplateLoaderTest() {
        super("*.ts");
    }

    private void assertUseTemplateForInvocationOnlyTemplate(boolean useTemplateForInvocationOnlySnippet,
                                                            @NotNull String snippet,
                                                            @NotNull String expectedTemplateText,
                                                            int expectedVariableCount,
                                                            int expectedSegmentsCount) {
        Template template = SnippetTemplateFactory.createTemplate(
                snippet,
                myFixture.getProject(),
                variableName -> variableName,
                null,
                useTemplateForInvocationOnlySnippet
        );

        assertEquals(expectedTemplateText.replace(END_PLACEHOLDER, ""), template.getTemplateText());
        assertEquals(expectedVariableCount, template.getVariables().size());
        int segmentsCount = template.getSegmentsCount();
        assertEquals(expectedSegmentsCount, segmentsCount);
        assertEquals(Template.END, template.getSegmentName(segmentsCount - 1));
        assertEquals(expectedTemplateText.indexOf(END_PLACEHOLDER), template.getSegmentOffset(segmentsCount - 1));
    }

    public void testUseTemplateForInvocationOnlyTemplate_enabled_invocation() {
        assertUseTemplateForInvocationOnlyTemplate(
                true,
                ".pow(${1:x}, ${2:y})$0",
                ".pow(, )<end>",
                2,
                3
        );
    }

    public void testUseTemplateForInvocationOnlyTemplate_disabled_invocation() {
        assertUseTemplateForInvocationOnlyTemplate(
                false,
                ".pow(${1:x}, ${2:y})$0",
                ".pow(<end>)",
                0,
                1
        );
    }

    public void testUseTemplateForInvocationOnlyTemplate_disabled_invocation_balancedNestedParens() {
        assertUseTemplateForInvocationOnlyTemplate(
                false,
                ".pow(${1:x}, Math.abs(${2:y}))$0",
                ".pow(<end>)",
                0,
                1
        );
    }

    public void testUseTemplateForInvocationOnlyTemplate_disabled_invocation_unbalancedNestedParens() {
        assertUseTemplateForInvocationOnlyTemplate(
                false,
                ".pow(${1:x}, Math.abs(${2:y})$0",
                ".pow(, Math.abs()<end>",
                2,
                3
        );
    }

    public void testUseTemplateForInvocationOnlyTemplate_disabled_notInvocation() {
        assertUseTemplateForInvocationOnlyTemplate(
                false,
                ".PI$0",
                ".PI<end>",
                0,
                1
        );
    }

    public void testUseTemplateForInvocationOnlyTemplate_disabled_invocation_extraVariables() {
        assertUseTemplateForInvocationOnlyTemplate(
                false,
                ".pow(${1:x}, ${2:y}) /* ${3:z} */$0",
                ".pow(, ) /*  */<end>",
                3,
                4
        );
    }
}

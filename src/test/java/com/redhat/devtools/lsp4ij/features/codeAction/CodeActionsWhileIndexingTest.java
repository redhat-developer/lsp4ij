/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.testFramework.DumbModeTestUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;

/**
 * LSP intentions are DumbAware, as IntelliJ 2025.3 and later offer an intention which isn't only in files they index,
 * and they don't index a file outside the project's content. While indexing, an LSP intention is not available, and it
 * sends no request.
 */
public class CodeActionsWhileIndexingTest extends LSPCodeActionFixtureTestCase {

    private static final String TITLE = "Insert a greeting";

    public CodeActionsWhileIndexingTest() {
        super("*.greeting");
    }

    public void testAnIntentionIsDumbAware() {
        assertTrue(DumbService.isDumbAware(greetingIntention()));
    }

    public void testAnIntentionIsNotAvailableAndSendsNoRequestWhileIndexing() {
        var intention = greetingIntention();
        var project = myFixture.getProject();
        int requests = codeActionRequests();

        DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
            // a range which needs a new request
            myFixture.getEditor().getCaretModel().moveToOffset(2);

            assertFalse(intention.isAvailable(project, myFixture.getEditor(), myFixture.getFile()));
            assertEquals(requests, codeActionRequests());
        });
    }

    private IntentionAction greetingIntention() {
        var actions = assertCodeActions("test.greeting", IntentionActionKind.INTENTION_ONLY, "hello world<caret>",
                // language=JSON
                """
                        [
                          {
                            "title": "%s",
                            "kind": "refactor"
                          }
                        ]""".formatted(TITLE),
                TITLE);
        return assertFindIntentionByText(TITLE, actions);
    }

    private static int codeActionRequests() {
        return MockLanguageServer.INSTANCE.getTextDocumentService().codeActionRequests;
    }
}

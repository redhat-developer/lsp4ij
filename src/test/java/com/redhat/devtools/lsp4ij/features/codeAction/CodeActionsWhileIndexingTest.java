/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeAction;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.testFramework.DumbModeTestUtils;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeActionFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;

import java.util.concurrent.TimeUnit;

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

    public void testAnIntentionIsNotAvailableAndSendsNoRequestWhileIndexing() throws Exception {
        var intention = greetingIntention();
        int requests = codeActionRequests();

        DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
            // a range which needs a new request
            myFixture.getEditor().getCaretModel().moveToOffset(2);

            assertFalse(isAvailable(intention));
            assertEquals(requests, codeActionRequests());
        });
    }

    /**
     * Asks in a read action on a pooled thread, as IntelliJ's intention pass does. There an intention waits for its code
     * actions, unlike on the EDT, so that a request it sends has been received when it answers.
     */
    private boolean isAvailable(IntentionAction intention) throws Exception {
        return ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() ->
                        intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())))
                .get(10, TimeUnit.SECONDS);
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

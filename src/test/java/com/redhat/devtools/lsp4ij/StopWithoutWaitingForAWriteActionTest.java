/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A stop of a language server from a thread of the pool doesn't wait for a write action of the UI thread: the stop
 * holds the monitor of the language server, which the UI thread can need in that write action.
 */
public class StopWithoutWaitingForAWriteActionTest extends LSPCodeInsightFixtureTestCase {

    public StopWithoutWaitingForAWriteActionTest() {
        super("*.foo");
    }

    public void testAStopFromAnotherThreadDoesNotWaitForTheWriteActionOfTheUIThread() throws Exception {
        var file = myFixture.configureByText("main.foo", "content");
        var wrapper = LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS)
                .get(0).getServerWrapper();
        // A connected file, whose support the stop disposes
        PlatformTestUtil.waitWithEventsDispatching("the file must be connected",
                () -> !wrapper.getOpenedDocuments().isEmpty(), 30);

        WriteAction.<Exception>run(() -> {
            var stopping = ApplicationManager.getApplication().executeOnPooledThread(() -> wrapper.stop());
            try {
                stopping.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                fail("the stop must not wait for the write action");
            }
        });
    }
}

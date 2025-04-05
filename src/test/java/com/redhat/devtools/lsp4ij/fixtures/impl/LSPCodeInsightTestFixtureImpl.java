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
package com.redhat.devtools.lsp4ij.fixtures.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.jetbrains.annotations.NotNull;

/**
 * {@link LSPCodeInsightTestFixture} implementation.
 */
public class LSPCodeInsightTestFixtureImpl extends CodeInsightTestFixtureImpl implements LSPCodeInsightTestFixture {
    public LSPCodeInsightTestFixtureImpl(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirTestFixture) {
        super(projectFixture, tempDirTestFixture);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset();
    }

    @Override
    public void selectItem(LookupElement item) {
        selectItem(item, (char)0);
    }

    @Override
    public void selectItem(@NotNull LookupElement item, final char completionChar) {
        final LookupImpl lookup = (LookupImpl) getLookup();
        lookup.setCurrentItem(item);
        if (LookupEvent.isSpecialCompletionChar(completionChar)) {
            lookup.finishLookup(completionChar);
        } else {
            type(completionChar);
        }
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
}

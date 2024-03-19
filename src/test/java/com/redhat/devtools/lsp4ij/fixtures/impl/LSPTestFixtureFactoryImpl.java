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

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;

/**
 * {@link LSPTestFixtureFactory} implementation.
 */
public class LSPTestFixtureFactoryImpl extends LSPTestFixtureFactory {
    @Override
    public LSPCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
        return createCodeInsightFixture(projectFixture, new TempDirTestFixtureImpl());
    }

    @Override
    public LSPCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
        return new LSPCodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
    }
}

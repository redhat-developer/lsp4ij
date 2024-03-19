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
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.impl.LSPTestFixtureFactoryImpl;

/**
 * LSP test fixture factory.
 */
public abstract class LSPTestFixtureFactory {

    private static final LSPTestFixtureFactory INSTANCE = new LSPTestFixtureFactoryImpl();

    public static LSPTestFixtureFactory getFixtureFactory() {
        return INSTANCE;
    }

    public abstract LSPCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture fixture);

    public abstract LSPCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture);
}

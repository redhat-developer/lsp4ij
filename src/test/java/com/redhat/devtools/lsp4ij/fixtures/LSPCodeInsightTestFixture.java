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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

/**
 * LSP API test fixture.
 */
public interface LSPCodeInsightTestFixture extends CodeInsightTestFixture {

    /**
     * Select the given completion item.
     *
     * @param item the completion item to select.
     */
    void selectItem(LookupElement item);

    /**
     * Select the given completion item.
     *
     * @param item           the completion item to select.
     * @param completionChar
     */
    void selectItem(@NotNull LookupElement item, final char completionChar);
}

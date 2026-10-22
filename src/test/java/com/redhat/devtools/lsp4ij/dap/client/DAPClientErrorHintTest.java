/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.testFramework.LightPlatformTestCase;

/**
 * The error hint of an exception stop is shown a second after the stop, on the editor of the stopped file,
 * which may have been closed in that second.
 */
public class DAPClientErrorHintTest extends LightPlatformTestCase {

    public void testNoHintOnAnEditorClosedBeforeItShows() {
        var editorFactory = EditorFactory.getInstance();
        var editor = editorFactory.createEditor(editorFactory.createDocument("throw new Error();\n"), getProject());
        editorFactory.releaseEditor(editor);

        // the editor of a stopped file, closed before the hint is shown
        DAPClient.doShowErrorHint(editor, "Exception has occurred: Error", 0);

        assertFalse(HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(false));
    }
}

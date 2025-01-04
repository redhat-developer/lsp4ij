/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DAPDebuggerEditorsProvider extends XDebuggerEditorsProvider {

    private final @Nullable FileType fileType;

    public DAPDebuggerEditorsProvider(@Nullable FileType fileType) {
        this.fileType = fileType;
    }

    @Override
    public @NotNull FileType getFileType() {
        return fileType != null ? fileType : PlainTextFileType.INSTANCE;
    }

    @Override
    public @NotNull Document createDocument(@NotNull Project project, @NotNull XExpression expression, @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
        // TODO: is it correct? (what about code fragment?)
        String text = expression.getExpression();
        return new DocumentImpl(text);
    }
}

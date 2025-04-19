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
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;

import static com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils.canReportProblem;

/**
 * LSP problem file highlight filter to show problem in the Project View when the file
 * which is associated to a language server have some LSP diagnostics with the error severity.
 */
public class LSPProblemFileHighlightFilter implements Condition<VirtualFile> {

    private final Project project;

    public LSPProblemFileHighlightFilter(Project project) {
        this.project = project;
    }

    @Override
    public boolean value(VirtualFile file) {
        if (project == null) {
            // I don't know if it is possible to have a null project, but we need to check that
            return false;
        }
        // Check if the given file can report problem.
        return canReportProblem(file, project);
    }
}
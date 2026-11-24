/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a virtual source file referenced in a DAP (Debug Adapter Protocol) session.
 * <p>
 * This class is a simple specialization of {@link DAPFile} and is used to represent
 * source files that exist in memory but may be referenced by a debug session.
 * <p>
 * The {@link #getPath()} method inherited from {@link DAPFile} has the form:
 * <pre>
 *   $projectLocationHash/$configName/$sourceReference/$sourceName
 * </pre>
 * Each source reference has its own file, named {@code $sourceName}, also when sources share a name, as the scripts
 * evaluated with {@code eval}, all named {@code <anonymous>}, do. The paths of earlier versions,
 * {@code $projectLocationHash/$configName/$sourceName}, name a file of that name.
 * The {@link #getUrl()} method inherited from {@link DAPFile} returns:
 * <pre>
 *   dap-file:///$path
 * </pre>
 * <p>
 * Since this file is virtual, it does not correspond to a real file on disk.
 * Its content can be accessed and modified through the {@link com.intellij.openapi.editor.Document}
 * obtained via {@link com.intellij.openapi.fileEditor.FileDocumentManager#getDocument(com.intellij.openapi.vfs.VirtualFile)}.
 */
public class SourceReferenceFile extends DAPFile {

    public SourceReferenceFile(String name,
                               @NotNull String path,
                               @NotNull Project project) {
        super(name, path, PlainTextFileType.INSTANCE, project);
    }
}

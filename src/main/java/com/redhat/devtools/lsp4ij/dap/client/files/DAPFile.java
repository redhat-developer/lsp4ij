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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a virtual, writable file in IntelliJ for content coming from a DAP client.
 * <p>
 * This class extends {@link LightVirtualFile}, so it exists entirely in memory
 * and does not correspond to a physical file on disk.
 * <p>
 * The {@link #path} field has the form:
 * <pre>
 *   $projectLocationHash/$configName/$sourceName
 * </pre>
 * where:
 * <ul>
 *   <li>$projectLocationHash = a hash or identifier for the project location</li>
 *   <li>$configName = the run/debug configuration name</li>
 *   <li>$sourceName = the name of the file or source reference</li>
 * </ul>
 * The {@link #getUrl()} method returns:
 * <pre>
 *   dap-file:///$path
 * </pre>
 * This URL is used by IntelliJ's {@link com.intellij.openapi.vfs.VirtualFileSystem} API.
 */
public abstract class DAPFile extends LightVirtualFile {

    /** Virtual file path in the form $projectLocationHash/$configName/$sourceName */
    private final @NotNull String path;

    /** URL used by IntelliJ VirtualFileSystem, automatically generated from path */
    private final @NotNull String url;

    /**
     * IntelliJ project associated with this disassembly file.
     */
    private final @NotNull Project project;
    private long sessionId;

    public DAPFile(@NlsSafe String name,
                   @NotNull String path,
                   @NotNull Project project) {
        super(name, DisassemblyFileType.INSTANCE, "", LocalTimeCounter.currentTime());
        this.project = project;
        this.path = path;
        this.url = DAPFileSystem.PROTOCOL + ":///" + getPath();
    }

    public boolean shouldReload(long sessionId) {
        if (this.sessionId == sessionId) {
            return false;
        }
        this.sessionId = sessionId;
        return true;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public @NotNull String getUrl() {
        return url;
    }

    @Override
    public @NotNull String getPath() {
        return path;
    }

    public @NotNull Project getProject() {
        return project;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DAPFile that = (DAPFile) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(url);
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
        return VirtualFileManager.getInstance().getFileSystem(DAPFileSystem.PROTOCOL);
    }
}

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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Represents a virtual, writable file in IntelliJ for content coming from a DAP client.
 * <p>
 * This class extends {@link LightVirtualFile}, so it exists entirely in memory
 * and does not correspond to a physical file on disk.
 * <p>
 * The {@link #path} field has the form:
 * <pre>
 *   $projectLocationHash/$configName/$sourceReference/$sourceName
 *   $projectLocationHash/$configName/disassembly
 * </pre>
 * where:
 * <ul>
 *   <li>$projectLocationHash = a hash or identifier for the project location</li>
 *   <li>$configName = the run/debug configuration name</li>
 *   <li>$sourceReference = the source reference of a source which the debug adapter gives the text of</li>
 *   <li>$sourceName = the name of the source, which is the name of the file</li>
 * </ul>
 * The {@link #getUrl()} method returns:
 * <pre>
 *   dap-file:///$path
 * </pre>
 * This URL is used by IntelliJ's {@link com.intellij.openapi.vfs.VirtualFileSystem} API.
 */
public abstract class DAPFile extends LightVirtualFile {

    /**
     * Virtual file path in the form $projectLocationHash/$configName/$sourceReference/$sourceName, or
     * $projectLocationHash/$configName/disassembly
     */
    private final @NotNull String path;

    /** URL used by IntelliJ VirtualFileSystem, automatically generated from path */
    private final @NotNull String url;

    /**
     * IntelliJ project associated with this disassembly file.
     */
    private final @NotNull Project project;
    private long sessionId;
    // The source reference whose text the document has, as the last load of a source reference left it, or null before
    // the first one. A source reference is valid only in its session, and the file outlives it: a later session, or
    // another session of the tree ('startDebugging'), may give another text for the same source reference. The client
    // is weakly referenced, so that the file doesn't keep it.
    private volatile @Nullable LoadedSource loadedSource;

    public DAPFile(@NlsSafe String name,
                   @NotNull String path,
                   @NotNull FileType fileType,
                   @NotNull Project project) {
        super(name, fileType, "", LocalTimeCounter.currentTime());
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

    /**
     * Says that the document of this file has the text which the debug adapter of the given client gave for the given
     * source reference.
     *
     * @param client          the client of the debug adapter which gave the text.
     * @param sourceReference the source reference whose text the document has.
     */
    public void setLoadedSource(@NotNull DAPClient client, int sourceReference) {
        loadedSource = new LoadedSource(new WeakReference<>(client), sourceReference);
    }

    /**
     * Returns whether the document of this file has the text which the debug adapter of the given client gave for the
     * given source reference. The file outlives the session of the source reference: the document may have the text
     * which another client gave for it.
     *
     * @param client          the client of the debug adapter which gives the text.
     * @param sourceReference the source reference.
     * @return true if the document has the text of the source reference, and false otherwise.
     */
    public boolean isLoadedSource(@NotNull DAPClient client, int sourceReference) {
        var loaded = loadedSource;
        return loaded != null && loaded.client().get() == client && loaded.sourceReference() == sourceReference;
    }

    private record LoadedSource(@NotNull Reference<DAPClient> client, int sourceReference) {
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

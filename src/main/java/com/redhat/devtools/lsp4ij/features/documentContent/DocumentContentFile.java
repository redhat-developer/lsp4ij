/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentContent;

import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/** Read-only server content whose LSP identity is never replaced with a disk path. */
public final class DocumentContentFile extends LightVirtualFile {

    private final URI uri;
    private volatile String content;

    DocumentContentFile(@NotNull URI uri, @NotNull String text) {
        super(fileName(uri), text);
        this.uri = uri;
        this.content = text;
        setWritable(false);
    }

    private static String fileName(URI uri) {
        var path = uri.getPath();
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return "document.txt";
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Replaces server-owned text without exposing a writable virtual file. */
    void replaceContent(@NotNull String text) {
        content = text;
        setModificationStamp(LocalTimeCounter.currentTime());
    }

    @Override
    public @NotNull CharSequence getContent() {
        // LightVirtualFile's public mutators reject read-only files. Its byte/input-stream
        // readers use getContent(), so this snapshot is their authoritative source too.
        return content != null ? content : super.getContent();
    }

    @Override
    public long getLength() {
        // The base class caches length in its writable setter, which this file never uses.
        return getContent().toString().getBytes(getCharset()).length;
    }

    public @NotNull URI getUri() {
        return uri;
    }

    @Override
    public @NotNull String getUrl() {
        return uri.toString();
    }

    @Override
    public @NotNull String getPath() {
        return uri.toString();
    }

    @Override
    public boolean isWritable() {
        return false;
    }
}

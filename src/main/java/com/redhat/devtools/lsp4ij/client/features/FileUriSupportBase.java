package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.internal.UriFormatter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public class FileUriSupportBase implements FileUriSupport{

    private final boolean encoded;

    public FileUriSupportBase(boolean encoded) {
        this.encoded = encoded;
    }

    @Override
    public @Nullable URI getFileUri(@NotNull VirtualFile file) {
        return LSPIJUtils.toUri(file);
    }

    @Override
    public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
        return LSPIJUtils.findResourceFor(fileUri);
    }

    @Override
    public @Nullable String toString(@NotNull URI fileUri) {
        if (encoded) {
            return UriFormatter.asFormatted(fileUri, false);
        }
        return fileUri.toASCIIString();
    }
}

/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Angelo Zerr - implementation of DAP disassembly support
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A special IntelliJ file type used to represent a disassembly view
 * generated from a Debug Adapter Protocol (DAP) session.
 * <p>
 * This file type:
 * <ul>
 *     <li>Represents virtual files containing machine-level disassembly instructions.</li>
 *     <li>Is <b>read-only</b> and does not map to any actual file on disk.</li>
 *     <li>Uses no file extension since it is only generated at runtime.</li>
 *     <li>Is specifically associated with instances of {@link DisassemblyFile}.</li>
 * </ul>
 */
public class DisassemblyFileType extends LanguageFileType {

    /**
     * Singleton instance of the disassembly file type.
     */
    public static final DisassemblyFileType INSTANCE = new DisassemblyFileType();

    protected DisassemblyFileType() {
        super(DisassemblyLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "Disassembly";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "lsp4ij";
    }

    @Override
    public Icon getIcon() {
        return AllIcons.FileTypes.Archive;
    }

    /**
     * Returns a description of this file type for display in settings or tooltips.
     *
     * @return a user-friendly description of the disassembly file type
     */
    @Override
    public @NotNull String getDescription() {
        return "Virtual file type representing machine-level disassembly instructions " +
                "generated from a Debug Adapter Protocol (DAP) debug session.";
    }

    /**
     * Returns the display name of this file type for use in IntelliJ UI components.
     *
     * @return a user-friendly display name
     */
    @Override
    public @Nls @NotNull String getDisplayName() {
        return "Disassembly View";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public @NonNls @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
        return super.getCharset(file, content);
    }
}

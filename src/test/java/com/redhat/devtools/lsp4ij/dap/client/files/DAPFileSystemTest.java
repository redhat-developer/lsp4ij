/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.files;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The files of the sources which a debug adapter gives by source reference, and their URLs: each source reference has
 * a file of its own, named after the source, also when sources share a name (#2196). The URLs of earlier versions,
 * without a source reference, as persisted breakpoints may have them, still resolve.
 */
public class DAPFileSystemTest extends LightPlatformTestCase {

    private static final String CONFIG = "evals";
    private static final String NAME = "<anonymous>";

    public void testSourcesOfTheSameNameWithDifferentSourceReferencesHaveFilesOfTheirOwn() {
        var registry = DAPFileRegistry.getInstance();
        var first = registry.getOrCreateDAPFile(CONFIG, 5, NAME, getProject());
        var second = registry.getOrCreateDAPFile(CONFIG, 9, NAME, getProject());

        assertNotSame(first, second);
        // named after the source, as their editor tabs are
        assertEquals(NAME, first.getName());
        assertEquals(NAME, second.getName());
        // with paths of their own
        assertEquals(hash() + "/" + CONFIG + "/5/" + NAME, first.getPath());
        assertEquals(hash() + "/" + CONFIG + "/9/" + NAME, second.getPath());
        assertSame("A source reference has one file", first, registry.getOrCreateDAPFile(CONFIG, 5, NAME, getProject()));
        // and their URLs resolve to them
        assertEquals("dap-file:///" + hash() + "/" + CONFIG + "/5/" + NAME, first.getUrl());
        assertSame(first, findFileByUrl(first.getUrl()));
        assertSame(second, findFileByUrl(second.getUrl()));
    }

    public void testTheURLOfAnEarlierVersionWithoutSourceReferenceStillResolves() {
        var file = findFileByUrl("dap-file:///" + hash() + "/" + CONFIG + "/" + NAME);

        assertInstanceOf(file, SourceReferenceFile.class);
        assertEquals(NAME, file.getName());
        assertEquals(hash() + "/" + CONFIG + "/" + NAME, file.getPath());
        assertNotSame(DAPFileRegistry.getInstance().getOrCreateDAPFile(CONFIG, 5, NAME, getProject()), file);
        // as does the one of the disassembly, which has no source reference
        var disassembly = findFileByUrl("dap-file:///" + hash() + "/" + CONFIG + "/" + DisassemblyFile.FILE_NAME);
        assertInstanceOf(disassembly, DisassemblyFile.class);
    }

    public void testAURLWithoutAValidSourceReferenceDoesNotResolve() {
        assertNull(findFileByUrl("dap-file:///" + hash() + "/" + CONFIG + "/0/" + NAME));
        assertNull(findFileByUrl("dap-file:///" + hash() + "/" + CONFIG + "/eval/" + NAME));
        assertNull(findFileByUrl("dap-file:///" + hash() + "/" + CONFIG));
        assertNull(findFileByUrl("dap-file:///" + hash() + "/" + CONFIG + "/5/"));
    }

    private @NotNull String hash() {
        return getProject().getLocationHash();
    }

    private static @Nullable VirtualFile findFileByUrl(@NotNull String url) {
        return VirtualFileManager.getInstance().findFileByUrl(url);
    }
}

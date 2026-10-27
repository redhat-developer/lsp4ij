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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.impl.rules.UsageType;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.client.features.LSPUsageFeature;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Tests for the read and write access of references, see {@link LSPUsageFeature#getReadWriteAccess(LSPUsagePsiElement)}.
 */
public class LSPUsageReadWriteAccessTest extends BasePlatformTestCase {

    private static final String TEXT = "value = value + 1";

    private static final Range WRITTEN = new Range(new Position(0, 0), new Position(0, 5));

    private static final Range READ = new Range(new Position(0, 8), new Position(0, 13));

    public void testUsageFeatureIsAskedForTheAccessOfReferences() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPClientFeatures clientFeatures = clientFeatures(file, reference -> reference.getTextRange().getStartOffset() == 0 ? Access.Write : Access.Read);

        assertEquals(Access.Write, toReference(file, clientFeatures, WRITTEN).getAccess());
        assertEquals(Access.Read, toReference(file, clientFeatures, READ).getAccess());
    }

    public void testUsageFeatureIsAskedInAReadActionOutsideOne() throws Exception {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        List<Boolean> readAccess = new ArrayList<>();
        LSPClientFeatures clientFeatures = clientFeatures(file, reference -> {
            readAccess.add(ApplicationManager.getApplication().isReadAccessAllowed());
            return Access.Write;
        });

        LSPUsagePsiElement reference = ApplicationManager.getApplication()
                .executeOnPooledThread(() -> toReference(file, clientFeatures, WRITTEN))
                .get(10, TimeUnit.SECONDS);

        assertEquals(Access.Write, reference.getAccess());
        assertEquals(List.of(true), readAccess);
    }

    public void testUsageFeatureIsNotAskedForOtherKinds() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        List<LSPUsagePsiElement> asked = new ArrayList<>();
        LSPClientFeatures clientFeatures = clientFeatures(file, reference -> {
            asked.add(reference);
            return Access.Write;
        });

        for (LSPUsagePsiElement.UsageKind kind : LSPUsagePsiElement.UsageKind.values()) {
            if (kind != LSPUsagePsiElement.UsageKind.references) {
                LSPUsagePsiElement usage = LSPUsagesManager.toPsiElement(location(WRITTEN), clientFeatures, kind, getProject());
                assertNotNull(kind.name(), usage);
                assertNull(kind.name(), usage.getAccess());
            }
        }
        assertEmpty(asked);
    }

    public void testAccessIsUnknownByDefault() {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPClientFeatures clientFeatures = new TestClientFeatures(file.getVirtualFile());

        assertNull(toReference(file, clientFeatures, WRITTEN).getAccess());
    }

    public void testReferenceWithUnknownAccessIsListedAsBefore() {
        LSPUsagePsiElement reference = reference(null);

        Usage usage = LSPUsageSearcher.toUsage(reference);

        assertFalse(usage instanceof ReadWriteAccessUsage);
        assertEquals(LanguageServerBundle.message("usage.type.references"), usageType(reference).toString());
    }

    public void testReadReference() {
        LSPUsagePsiElement reference = reference(Access.Read);

        ReadWriteAccessUsage usage = assertInstanceOf(LSPUsageSearcher.toUsage(reference), ReadWriteAccessUsage.class);

        assertTrue(usage.isAccessedForReading());
        assertFalse(usage.isAccessedForWriting());
        assertSame(UsageType.READ, usageType(reference));
    }

    public void testWriteReference() {
        LSPUsagePsiElement reference = reference(Access.Write);

        ReadWriteAccessUsage usage = assertInstanceOf(LSPUsageSearcher.toUsage(reference), ReadWriteAccessUsage.class);

        assertFalse(usage.isAccessedForReading());
        assertTrue(usage.isAccessedForWriting());
        assertSame(UsageType.WRITE, usageType(reference));
    }

    public void testReadWriteReferenceIsGroupedAsAWrite() {
        LSPUsagePsiElement reference = reference(Access.ReadWrite);

        ReadWriteAccessUsage usage = assertInstanceOf(LSPUsageSearcher.toUsage(reference), ReadWriteAccessUsage.class);

        assertTrue(usage.isAccessedForReading());
        assertTrue(usage.isAccessedForWriting());
        assertSame(UsageType.WRITE, usageType(reference));
    }

    public void testAccessDoesNotChangeTheGroupOfOtherKinds() {
        LSPUsagePsiElement definition = reference(Access.Write);
        definition.setKind(LSPUsagePsiElement.UsageKind.definitions);

        assertEquals(LanguageServerBundle.message("usage.type.definitions"), usageType(definition).toString());
    }

    private @NotNull LSPUsagePsiElement reference(@Nullable Access access) {
        PsiFile file = myFixture.configureByText("test.txt", TEXT);
        LSPUsagePsiElement reference = new LSPUsagePsiElement(file, new TextRange(0, 5));
        reference.setKind(LSPUsagePsiElement.UsageKind.references);
        reference.setAccess(access);
        return reference;
    }

    private static UsageType usageType(@NotNull LSPUsagePsiElement usage) {
        return new LSPUsageTypeProvider().getUsageType(usage);
    }

    private @NotNull LSPUsagePsiElement toReference(@NotNull PsiFile file,
                                                    @NotNull LSPClientFeatures clientFeatures,
                                                    @NotNull Range range) {
        LSPUsagePsiElement reference = LSPUsagesManager.toPsiElement(location(range), clientFeatures, LSPUsagePsiElement.UsageKind.references, file.getProject());
        assertNotNull(reference);
        return reference;
    }

    private static @NotNull Location location(@NotNull Range range) {
        return new Location("file:///test.txt", range);
    }

    private static @NotNull LSPClientFeatures clientFeatures(@NotNull PsiFile file,
                                                             @NotNull Function<LSPUsagePsiElement, Access> access) {
        return new TestClientFeatures(file.getVirtualFile()).setUsageFeature(new LSPUsageFeature() {
            @Override
            public @Nullable Access getReadWriteAccess(@NotNull LSPUsagePsiElement reference) {
                return access.apply(reference);
            }
        });
    }

    /**
     * Resolves every URI to the test file, which lives in a file system with no file URIs.
     */
    private static class TestClientFeatures extends LSPClientFeatures {

        private final @NotNull VirtualFile file;

        TestClientFeatures(@NotNull VirtualFile file) {
            this.file = file;
        }

        @Override
        public @Nullable VirtualFile findFileByUri(@NotNull String fileUri) {
            return file;
        }
    }
}

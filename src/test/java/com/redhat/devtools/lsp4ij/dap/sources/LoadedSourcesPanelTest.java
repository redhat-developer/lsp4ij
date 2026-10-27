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
package com.redhat.devtools.lsp4ij.dap.sources;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.debug.LoadedSourceEventArguments;
import org.eclipse.lsp4j.debug.LoadedSourceEventArgumentsReason;
import org.eclipse.lsp4j.debug.Source;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Loaded Sources panel shows the sources which the DAP server reports, in path order, and its 'loadedSource' events
 * add, change and remove them.
 */
public class LoadedSourcesPanelTest extends BasePlatformTestCase {

    private final List<Source> opened = new ArrayList<>();

    public void testShowsTheSourcesInPathOrderAndTheEventsUpdateThem() {
        var panel = new LoadedSourcesPanel(opened::add);
        panel.refresh(new Source[]{source("/work/b.x", null), source("/work/a.x", null)});
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(List.of("/work/a.x", "/work/b.x"), paths(panel));

        // a new source joins in order, a changed one replaces the one with its path, and a removed one goes
        panel.update(event(LoadedSourceEventArgumentsReason.NEW, source("/work/aa.x", null)));
        panel.update(event(LoadedSourceEventArgumentsReason.CHANGED, source("/work/b.x", null, "changed")));
        panel.update(event(LoadedSourceEventArgumentsReason.REMOVED, source("/work/a.x", null)));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(List.of("/work/aa.x", "/work/b.x"), paths(panel));
        assertEquals("changed", panel.getSources().get(1).getOrigin());
        assertEmpty(opened);
    }

    public void testASourceWithoutAPathIsTheOneWithItsSourceReference() {
        var panel = new LoadedSourcesPanel(opened::add);
        panel.refresh(new Source[]{source(null, 7), source(null, 8)});
        panel.update(event(LoadedSourceEventArgumentsReason.REMOVED, source(null, 7)));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(List.of(8), panel.getSources().stream().map(Source::getSourceReference).toList());

        assertTrue(LoadedSourcesPanel.sameSource(source("/work/a.x", 1), source("/work/a.x", 2)));
        assertFalse(LoadedSourcesPanel.sameSource(source("/work/a.x", 1), source(null, 1)));
        assertFalse(LoadedSourcesPanel.sameSource(source(null, null), source(null, null)));
        assertEquals("a.x", LoadedSourcesPanel.name(source("/work/a.x", null)));
    }

    private static List<String> paths(LoadedSourcesPanel panel) {
        return panel.getSources().stream().map(Source::getPath).toList();
    }

    private static Source source(@Nullable String path, @Nullable Integer sourceReference) {
        return source(path, sourceReference, null);
    }

    private static Source source(@Nullable String path, @Nullable Integer sourceReference, @Nullable String origin) {
        var source = new Source();
        source.setPath(path);
        source.setSourceReference(sourceReference);
        source.setOrigin(origin);
        if (path == null && sourceReference != null) {
            source.setName("source " + sourceReference);
        }
        return source;
    }

    private static LoadedSourceEventArguments event(LoadedSourceEventArgumentsReason reason, Source source) {
        var args = new LoadedSourceEventArguments();
        args.setReason(reason);
        args.setSource(source);
        return args;
    }
}

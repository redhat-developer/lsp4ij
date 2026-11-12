/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.codeLens.CodeLensDataResult;
import com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensEditorFactoryListener;
import com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensSupport;
import com.redhat.devtools.lsp4ij.features.color.LSPColorProvider;
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A feature which waits for a language server which cannot start, as when its command is wrong, gets nothing. It
 * doesn't throw the start failure, which IntelliJ would report as an IDE error, as LSP4IJ already reports it with a
 * "Cannot start server" notification.
 */
public class ServerStartFailureTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private CannotStartServerDefinition serverDefinition;

    public void testNoColorIsCollected() {
        PsiFile file = myFixture.configureByText("main.foo", "color: #ff0000");
        List<String> calls = new ArrayList<>();
        var sink = (InlayHintsSink) Proxy.newProxyInstance(
                InlayHintsSink.class.getClassLoader(),
                new Class<?>[]{InlayHintsSink.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return null;
                });
        var collector = new LSPColorProvider().getCollectorFor(file, myFixture.getEditor(), new NoSettings(), sink);
        assertNotNull(collector);

        collector.collect(file, myFixture.getEditor(), sink);

        assertEmpty(calls);
        assertServerCannotStart(file.getProject());
    }

    public void testNoFoldingRangeIsCollected() {
        PsiFile file = myFixture.configureByText("main.foo", "{\n}");

        assertEmpty(LSPFoldingRangeBuilder.getFoldingRanges(file));
        assertServerCannotStart(file.getProject());
    }

    public void testAScrollOfAnEditorWhoseCodeLensesFailedDoesNotThrow() {
        PsiFile file = myFixture.configureByText("main.foo", "a\nb\nc\n");
        var scrolled = listenToScrolls((EditorEx) myFixture.getEditor());
        failCodeLenses(file, scrolled.editor());

        // A scroll of the editor, on the UI thread
        scrolled.listener().visibleAreaChanged(new VisibleAreaEvent(scrolled.editor(),
                new Rectangle(0, 0, 800, 600), new Rectangle(0, 10, 800, 600)));
    }

    public void testAScrollOfAnEditorWhoseCodeLensesFailedUpdatesItsViewport() {
        // The code lenses of a later result, as once the server is fixed and restarted, are resolved in the viewport
        PsiFile file = myFixture.configureByText("main.foo", "line\n".repeat(100));
        var scrolled = listenToScrolls((EditorEx) myFixture.getEditor());
        failCodeLenses(file, scrolled.editor());
        int lineHeight = scrolled.editor().getLineHeight();
        var top = new Rectangle(0, 0, 800, 10 * lineHeight);
        var below = new Rectangle(0, 50 * lineHeight, 800, 10 * lineHeight);
        scrolled.listener().visibleAreaChanged(new VisibleAreaEvent(scrolled.editor(), new Rectangle(), top));

        scrolled.listener().visibleAreaChanged(new VisibleAreaEvent(scrolled.editor(), top, below));

        assertEquals(50, LSPCodeLensEditorFactoryListener.getCodeLensResolveContext(scrolled.editor()).getFirstViewportLine());
    }

    /**
     * An editor and the listener that LSPCodeLensEditorFactoryListener attaches to its scrolling model.
     */
    private record ScrolledEditor(EditorEx editor, VisibleAreaListener listener) {
    }

    /**
     * Returns the editor with the listener that LSPCodeLensEditorFactoryListener attaches to its scrolling model. The
     * visible area of an editor never changes in a headless test, so the listener is called directly.
     */
    private static ScrolledEditor listenToScrolls(EditorEx editor) {
        var scrollingModel = editor.getScrollingModel();
        List<VisibleAreaListener> listeners = new ArrayList<>();
        var listenedScrollingModel = (ScrollingModel) Proxy.newProxyInstance(ScrollingModel.class.getClassLoader(),
                new Class<?>[]{ScrollingModel.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addVisibleAreaListener")) {
                        listeners.add((VisibleAreaListener) args[0]);
                        return null;
                    }
                    return invoke(method, scrollingModel, args);
                });
        var listenedEditor = (EditorEx) Proxy.newProxyInstance(EditorEx.class.getClassLoader(),
                new Class<?>[]{EditorEx.class}, (proxy, method, args) -> method.getName().equals("getScrollingModel")
                        ? listenedScrollingModel : invoke(method, editor, args));
        new LSPCodeLensEditorFactoryListener().editorCreated(new EditorFactoryEvent(EditorFactory.getInstance(), listenedEditor));
        assertSize(1, listeners);
        return new ScrolledEditor(listenedEditor, listeners.get(0));
    }

    /**
     * Asks for the code lenses of the file for the editor, as LSPCodeLensProvider does for each editor of the file when
     * code vision asks for them, with a request that fails as when the language server cannot start. The request of a
     * language server that cannot start doesn't always fail: when the start fails before {@code start()} returns, or
     * as another feature's start replaces it, the request finds no language server and has no code lenses, which these
     * tests are not about. So this request fails every time.
     */
    private static void failCodeLenses(PsiFile file, EditorEx editor) {
        var support = new LSPCodeLensSupport(file) {
            @Override
            protected CompletableFuture<CodeLensDataResult> doLoad(@NotNull CodeLensParams params,
                                                                   @NotNull CancellationSupport cancellationSupport) {
                return CompletableFuture.failedFuture(new CannotStartProcessException("Cannot run program \"missing\""));
            }
        };
        LSPCodeLensEditorFactoryListener.setCodelensSupport(editor, support);
        var codeLenses = support.getCodeLenses(new CodeLensParams(new TextDocumentIdentifier()));
        assertTrue(codeLenses.isCompletedExceptionally());
        assertSame(codeLenses, support.getFuture());
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void assertServerCannotStart(@NotNull Project project) {
        assertInstanceOf(startedServer(project).getServerError(), CannotStartProcessException.class);
    }

    private @NotNull LanguageServerWrapper startedServer(@NotNull Project project) {
        // A feature of the open file starts the language server in the background
        PlatformTestUtil.waitWithEventsDispatching("a feature of the open file starts the language server",
                () -> findStartedServer(project) != null, 60);
        return Objects.requireNonNull(findStartedServer(project));
    }

    private @Nullable LanguageServerWrapper findStartedServer(@NotNull Project project) {
        return LanguageServiceAccessor.getInstance(project).getStartedServers()
                .stream()
                .filter(server -> server.getServerDefinition() == serverDefinition)
                .findFirst()
                .orElse(null);
    }

    /**
     * Server definition whose connection cannot start, as when its command is wrong.
     */
    private static class CannotStartServerDefinition extends MockLanguageServerDefinition {

        CannotStartServerDefinition() {
            super("test-server-cannot-start", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            return new MockConnectionProvider(getServer()) {
                @Override
                public void start() throws CannotStartProcessException {
                    throw new CannotStartProcessException("Cannot run program \"missing\"");
                }
            };
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory()
                .createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new CannotStartServerDefinition();
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(
                List.of("*.foo"),
                null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        serverDefinition.getServer().waitBeforeTearDown();
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }
}

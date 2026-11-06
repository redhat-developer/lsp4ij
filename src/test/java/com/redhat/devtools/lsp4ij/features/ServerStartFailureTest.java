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
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensEditorFactoryListener;
import com.redhat.devtools.lsp4ij.features.color.LSPColorProvider;
import com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;

import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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
        // The visible area of an editor never changes in a headless test, so the listener that
        // LSPCodeLensEditorFactoryListener attaches to the editor's scrolling model is taken to be called directly
        var editor = (EditorEx) myFixture.getEditor();
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
        // What LSPCodeLensProvider does for each editor of the file when code vision asks for its lenses
        var support = LSPFileSupport.getSupport(file).getCodeLensSupport();
        LSPCodeLensEditorFactoryListener.setCodelensSupport(listenedEditor, support);
        var codeLenses = support.getCodeLenses(new CodeLensParams(new TextDocumentIdentifier()));
        PlatformTestUtil.waitWithEventsDispatching("the code lens request must end", codeLenses::isDone, 10);
        assertTrue(codeLenses.isCompletedExceptionally());

        // A scroll of the editor, on the UI thread
        listeners.get(0).visibleAreaChanged(new VisibleAreaEvent(listenedEditor,
                new Rectangle(0, 0, 800, 600), new Rectangle(0, 10, 800, 600)));

        assertServerCannotStart(file.getProject());
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void assertServerCannotStart(@NotNull Project project) {
        var wrapper = LanguageServiceAccessor.getInstance(project).getStartedServers()
                .stream()
                .filter(server -> server.getServerDefinition() == serverDefinition)
                .findFirst()
                .orElseThrow();
        assertInstanceOf(wrapper.getServerError(), CannotStartProcessException.class);
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

/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.extension;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tests that a language server from the {@code server} extension point gets the root of a language server instance in
 * its factory.
 */
public class ExtensionServerInstancesTest extends BasePlatformTestCase {

    private static final WorkspaceFolder ROOT = new WorkspaceFolder("file:///project/a", "a");

    public void testAFactoryThatOverridesTheMethodsGetsTheRoot() {
        var extension = extension(FactoryWithRoot.class);
        var definition = new ExtensionLanguageServerDefinition(extension);

        var provider = definition.createConnectionProvider(getProject(), ROOT);
        var client = disposedWithTheTest(definition.createLanguageClient(getProject(), ROOT));

        var factory = (FactoryWithRoot) extension.getInstance();
        assertSame(factory.provider, provider);
        assertSame(factory.client, client);
        assertEquals(List.of(ROOT, ROOT), factory.roots);
    }

    public void testAFactoryThatDoesNotOverrideTheMethodsCreatesWithoutARoot() {
        var extension = extension(FactoryWithoutRoot.class);
        var definition = new ExtensionLanguageServerDefinition(extension);

        var provider = definition.createConnectionProvider(getProject(), ROOT);
        var client = disposedWithTheTest(definition.createLanguageClient(getProject(), ROOT));

        var factory = (FactoryWithoutRoot) extension.getInstance();
        assertSame(factory.provider, provider);
        assertSame(factory.client, client);
    }

    public void testWithoutARootTheFactoryIsNotGivenOne() {
        var extension = extension(FactoryWithRoot.class);
        var definition = new ExtensionLanguageServerDefinition(extension);

        definition.createConnectionProvider(getProject());
        disposedWithTheTest(definition.createLanguageClient(getProject()));

        assertEmpty(((FactoryWithRoot) extension.getInstance()).roots);
    }

    public void testAFailingConnectionProviderIsReported() {
        var definition = new ExtensionLanguageServerDefinition(extension(FailingFactory.class));

        try {
            definition.createConnectionProvider(getProject(), ROOT);
            fail("The failure of the factory is not reported");
        } catch (RuntimeException e) {
            assertInstanceOf(e.getCause(), IllegalStateException.class);
        }
    }

    public void testAFailingLanguageClientGivesTheDefaultOne() {
        var definition = new ExtensionLanguageServerDefinition(extension(FailingFactory.class));

        var client = disposedWithTheTest(definition.createLanguageClient(getProject(), ROOT));

        assertEquals(LanguageClientImpl.class, client.getClass());
    }

    /**
     * A language client listens to the settings from its constructor on, until it is disposed.
     */
    private @NotNull LanguageClientImpl disposedWithTheTest(@NotNull LanguageClientImpl client) {
        Disposer.register(getTestRootDisposable(), client);
        return client;
    }

    /**
     * Creates the extension as IntelliJ does from the {@code plugin.xml}: the platform creates the factory.
     */
    private static @NotNull ServerExtensionPointBean extension(@NotNull Class<? extends LanguageServerFactory> factory) {
        var extension = new ServerExtensionPointBean();
        extension.id = "extension-instances";
        extension.name = "Extension instances";
        extension.factoryClass = factory.getName();
        extension.setPluginDescriptor(Objects.requireNonNull(PluginManagerCore.getPlugin(PluginId.getId("com.redhat.devtools.lsp4ij"))));
        return extension;
    }

    public static class FactoryWithoutRoot implements LanguageServerFactory {

        StreamConnectionProvider provider;
        LanguageClientImpl client;

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            provider = new MockConnectionProvider(new MockLanguageServer(MockLanguageServer::defaultServerCapabilities));
            return provider;
        }

        @Override
        public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
            client = new LanguageClientImpl(project);
            return client;
        }
    }

    public static final class FactoryWithRoot extends FactoryWithoutRoot {

        final List<WorkspaceFolder> roots = new ArrayList<>();

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project,
                                                                          @NotNull WorkspaceFolder instanceRoot) {
            roots.add(instanceRoot);
            return createConnectionProvider(project);
        }

        @Override
        public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project,
                                                                @NotNull WorkspaceFolder instanceRoot) {
            roots.add(instanceRoot);
            return createLanguageClient(project);
        }
    }

    public static final class FailingFactory implements LanguageServerFactory {

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            throw new IllegalStateException("No connection provider");
        }

        @Override
        public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
            throw new IllegalStateException("No language client");
        }
    }
}

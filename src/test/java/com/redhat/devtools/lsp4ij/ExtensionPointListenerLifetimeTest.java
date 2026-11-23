/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.google.gson.JsonParser;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskFactoryPointBean;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.installation.definition.tasks.FileExistsTaskFactory;
import com.redhat.devtools.lsp4ij.server.definition.extension.ServerExtensionPointBean;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The extension point listeners of {@link LanguageServersRegistry} and {@link ServerInstallerManager} hear of the
 * extensions that are added and removed until their service is disposed, and no longer after.
 */
public class ExtensionPointListenerLifetimeTest extends BasePlatformTestCase {

    public void testTheRegistryHearsOfExtensionsUntilItIsDisposed() throws Exception {
        var registry = newService(LanguageServersRegistry.class);
        var unload = Disposer.newDisposable();
        try {
            var removed = Disposer.newDisposable(unload, "removed");
            ServerExtensionPointBean.EP_NAME.getPoint().registerExtension(server("added-server"), removed);
            assertNotNull(registry.getServerDefinition("added-server"));
            Disposer.dispose(removed);
            assertNull(registry.getServerDefinition("added-server"));

            Disposer.dispose(registry);
            ServerExtensionPointBean.EP_NAME.getPoint().registerExtension(server("added-server"), unload);
            assertNull(registry.getServerDefinition("added-server"));
        } finally {
            Disposer.dispose(unload);
        }
    }

    public void testTheInstallerManagerHearsOfExtensionsUntilItIsDisposed() throws Exception {
        var manager = newService(ServerInstallerManager.class);
        var unload = Disposer.newDisposable();
        try {
            var removed = Disposer.newDisposable(unload, "removed");
            InstallerTaskFactoryPointBean.EP_NAME.getPoint().registerExtension(installerTask(), removed);
            assertTrue(hasTask(manager));
            Disposer.dispose(removed);
            assertFalse(hasTask(manager));

            Disposer.dispose(manager);
            InstallerTaskFactoryPointBean.EP_NAME.getPoint().registerExtension(installerTask(), unload);
            assertFalse(hasTask(manager));
        } finally {
            Disposer.dispose(unload);
        }
    }

    /**
     * Creates a service as the platform does, with its private constructor, apart from the one of the application.
     */
    private static <T> @NotNull T newService(@NotNull Class<T> serviceClass) throws ReflectiveOperationException {
        var constructor = serviceClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static @NotNull ServerExtensionPointBean server(@NotNull String id) {
        var server = new ServerExtensionPointBean();
        server.id = id;
        server.name = id;
        server.factoryClass = LanguageServerFactory.class.getName();
        server.setPluginDescriptor(lsp4ij());
        return server;
    }

    private static @NotNull InstallerTaskFactoryPointBean installerTask() {
        var task = new InstallerTaskFactoryPointBean();
        task.type = "listenedTask";
        task.factoryClass = FileExistsTaskFactory.class.getName();
        task.setPluginDescriptor(lsp4ij());
        return task;
    }

    private static boolean hasTask(@NotNull ServerInstallerManager manager) {
        var task = JsonParser.parseString("{\"listenedTask\": {\"file\": \"/no/such/server\"}}").getAsJsonObject();
        return manager.loadTask(task, manager.loadInstaller("{}")) != null;
    }

    private static @NotNull PluginDescriptor lsp4ij() {
        return Objects.requireNonNull(PluginManagerCore.getPlugin(PluginId.getId("com.redhat.devtools.lsp4ij")));
    }
}

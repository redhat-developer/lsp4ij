/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin utilities.
 */
public class PluginUtils {

    private PluginUtils() {
    }

    /**
     * Returns the plugin descriptor for the given class.
     *
     * @param clazz the class
     * @return the plugin descriptor
     * @throws IllegalStateException if the plugin descriptor cannot be retrieved
     */
    @NotNull
    public static PluginDescriptor getPluginDescriptor(@NotNull Class<?> clazz) {
        ClassLoader classLoader = clazz.getClassLoader();
        if (classLoader instanceof PluginAwareClassLoader pluginAwareClassLoader) {
            return pluginAwareClassLoader.getPluginDescriptor();
        }
        throw new IllegalStateException("Unable to get plugin descriptor for class: " + clazz.getName());
    }

    /**
     * Returns true if the plugin with the given ID is installed, false otherwise.
     *
     * @param pluginId the plugin ID
     * @return true if the plugin is installed, false otherwise
     */
    public static boolean isPluginInstalled(@NotNull String pluginId) {
        return isPluginInstalled(PluginId.getId(pluginId));
    }

    /**
     * Returns true if the plugin with the given ID is installed, false otherwise.
     *
     * @param pluginId the plugin ID
     * @return true if the plugin is installed, false otherwise
     */
    public static boolean isPluginInstalled(@NotNull PluginId pluginId) {
        return PluginManager.isPluginInstalled(pluginId);
    }
}

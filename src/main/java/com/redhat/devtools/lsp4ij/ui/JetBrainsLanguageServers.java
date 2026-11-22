/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * The language servers of IntelliJ's own LSP client, which users define in <i>Settings | Languages &amp; Frameworks |
 * Language Servers</i>, a page of the plugin <i>LSP Connection Manager</i> ({@code com.intellij.lsp.ui}) that IntelliJ
 * bundles from 2026.3 on (#1933).
 * <p>
 * Their list isn't public API, and LSP4IJ doesn't depend on IntelliJ's LSP module. So the servers are read in the
 * form that IntelliJ stores them in: the state of the settings service, which is loaded through the class loader of
 * that page. Without the page, or if the state can't be read, there are no servers.
 */
@ApiStatus.Internal
public final class JetBrainsLanguageServers {

    private static final Logger LOGGER = Logger.getInstance(JetBrainsLanguageServers.class);

    /**
     * The id of the settings page.
     */
    public static final String CONFIGURABLE_ID = "lsp.servers";

    /**
     * The settings service of the page, in IntelliJ's LSP module.
     */
    static final String SETTINGS_CLASS = "com.intellij.platform.lsp.api.LspIntegrationSettings";

    /**
     * A language server of the page.
     *
     * @param name         its name.
     * @param enabled      whether IntelliJ starts it.
     * @param filePatterns the file name patterns of the files it serves, such as {@code *.lsptest}.
     */
    public record Server(@NotNull String name, boolean enabled, @NotNull List<String> filePatterns) {

        /**
         * Returns whether the server serves a file with the given name, as IntelliJ matches it.
         *
         * @param fileName the name of the file.
         * @return whether one of the file name patterns of the server matches the name.
         */
        public boolean matches(@NotNull CharSequence fileName) {
            var matchers = FileNameMatcherFactory.getInstance();
            return filePatterns.stream().anyMatch(pattern -> matchers.createMatcher(pattern).acceptsCharSequence(fileName));
        }
    }

    private JetBrainsLanguageServers() {
    }

    /**
     * Returns the language servers of the page in the given project.
     *
     * @param project the project.
     * @return the servers, enabled or not, or none if the page isn't there or its settings can't be read.
     */
    public static @NotNull List<Server> getServers(@NotNull Project project) {
        return getServers(project, SETTINGS_CLASS);
    }

    static @NotNull List<Server> getServers(@NotNull Project project, @NotNull String settingsClassName) {
        try {
            ClassLoader loader = getClassLoaderOfPage(project);
            if (loader == null) {
                return List.of();
            }
            Object settings = project.getService(Class.forName(settingsClassName, true, loader));
            if (!(settings instanceof PersistentStateComponent<?> component)) {
                return List.of();
            }
            Object state = component.getState();
            return state != null ? parse(XmlSerializer.serialize(state)) : List.of();
        } catch (RuntimeException e) {
            if (e instanceof ControlFlowException || e instanceof CancellationException) {
                throw e;
            }
            LOGGER.debug("Cannot read the language servers of IntelliJ's settings", e);
            return List.of();
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.debug("Cannot read the language servers of IntelliJ's settings", e);
            return List.of();
        }
    }

    private static @Nullable ClassLoader getClassLoaderOfPage(@NotNull Project project) {
        for (ConfigurableEP<Configurable> page : Configurable.PROJECT_CONFIGURABLE.getExtensions(project)) {
            if (CONFIGURABLE_ID.equals(page.id)) {
                return page.getPluginDescriptor().getClassLoader();
            }
        }
        return null;
    }

    /**
     * Returns the servers of the given state of the settings service, as IntelliJ stores it:
     * <pre>{@code
     * <servers>
     *   <server name="probe" enabled="true" ...>
     *     <filePatterns>*.lsptest; Makefile</filePatterns>
     *   </server>
     * </servers>
     * }</pre>
     *
     * @param state the state.
     * @return its servers.
     */
    static @NotNull List<Server> parse(@NotNull Element state) {
        Element servers = state.getChild("servers");
        if (servers == null) {
            return List.of();
        }
        return servers.getChildren("server").stream()
                .map(server -> new Server(
                        server.getAttributeValue("name", ""),
                        // As IntelliJ, which leaves out a default value, enables a server by default
                        !"false".equals(server.getAttributeValue("enabled")),
                        filePatternsOf(server.getChildText("filePatterns"))))
                .toList();
    }

    /**
     * Returns the file name patterns of the given text, which separates them with {@code ;}, as IntelliJ splits them.
     */
    private static @NotNull List<String> filePatternsOf(@Nullable String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split(";"))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .toList();
    }
}

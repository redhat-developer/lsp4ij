/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Language server registry.
 */
public class LanguageServersRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServersRegistry.class);

    public abstract static class LanguageServerDefinition {

        enum Scope {
            project, application
        }

        private static final int DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT = 5;

        public final @NotNull
        String id;
        public final @NotNull
        String label;
        public final boolean isSingleton;
        public final @NotNull
        Map<Language, String> languageIdMappings;
        public final String description;
        public final int lastDocumentDisconnectedTimeout;
        private boolean enabled;

        public final boolean supportsLightEdit;

        final @NotNull Scope scope;

        public LanguageServerDefinition(@NotNull String id, @NotNull String label, String description, boolean isSingleton, Integer lastDocumentDisconnectedTimeout, String scope, boolean supportsLightEdit) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.isSingleton = isSingleton;
            this.lastDocumentDisconnectedTimeout = lastDocumentDisconnectedTimeout != null && lastDocumentDisconnectedTimeout > 0 ? lastDocumentDisconnectedTimeout : DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT;
            this.languageIdMappings = new ConcurrentHashMap<>();
            this.scope = scope == null || scope.isBlank() ? Scope.application : Scope.valueOf(scope);
            this.supportsLightEdit = supportsLightEdit;
            setEnabled(true);
        }


        /**
         * Returns true if the language server definition is enabled and false otherwise.
         *
         * @return true if the language server definition is enabled and false otherwise.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Set enabled the language server definition.
         *
         * @param enabled enabled the language server definition.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void registerAssociation(@NotNull Language language, @NotNull String languageId) {
            this.languageIdMappings.put(language, languageId);
        }

        @NotNull
        public String getDisplayName() {
            return label != null ? label : id;
        }

        public abstract StreamConnectionProvider createConnectionProvider(Project project);

        public LanguageClientImpl createLanguageClient(Project project) {
            return new LanguageClientImpl(project);
        }

        public Class<? extends LanguageServer> getServerInterface() {
            return LanguageServer.class;
        }

        public <S extends LanguageServer> Launcher.Builder<S> createLauncherBuilder() {
            return new Launcher.Builder<>();
        }

        public boolean supportsCurrentEditMode(@NotNull Project project) {
            return project != null && (supportsLightEdit || !LightEdit.owns(project));
        }

        public Icon getIcon() {
            return AllIcons.Webreferences.Server;
        }
    }

    static class ExtensionLanguageServerDefinition extends LanguageServerDefinition {
        private final ServerExtensionPointBean extension;

        public ExtensionLanguageServerDefinition(ServerExtensionPointBean element) {
            super(element.id, element.label, element.description, element.singleton, element.lastDocumentDisconnectedTimeout, element.scope, element.supportsLightEdit);
            this.extension = element;
        }

        @Override
        public StreamConnectionProvider createConnectionProvider(Project project) {
            String serverImpl = extension.getImplementationClassName();
            if (serverImpl == null || serverImpl.isEmpty()) {
                throw new RuntimeException(
                        "Exception occurred while creating an instance of the stream connection provider, you have to define server/@class attribute in the extension point."); //$NON-NLS-1$
            }
            try {
                if (Scope.project == scope) {
                    return (StreamConnectionProvider) project.instantiateClassWithConstructorInjection(extension.getServerImpl(), project,
                            extension.getPluginDescriptor().getPluginId());
                }
                return (StreamConnectionProvider) project.instantiateClass(extension.getServerImpl(), extension.getPluginDescriptor().getPluginId());
            } catch (Exception e) {
                throw new RuntimeException(
                        "Exception occurred while creating an instance of the stream connection provider", e); //$NON-NLS-1$
            }
        }

        @Override
        public LanguageClientImpl createLanguageClient(Project project) {
            String clientImpl = extension.clientImpl;
            if (clientImpl != null && !clientImpl.isEmpty()) {
                try {
                    return (LanguageClientImpl) project.instantiateClass(extension.getClientImpl(),
                            extension.getPluginDescriptor().getPluginId());
                } catch (ClassNotFoundException e) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
            }
            return super.createLanguageClient(project);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<? extends LanguageServer> getServerInterface() {
            String serverInterface = extension.serverInterface;
            if (serverInterface != null && !serverInterface.isEmpty()) {
                try {
                    return (Class<? extends LanguageServer>) (Class<?>) extension.getServerInterface();
                } catch (ClassNotFoundException exception) {
                    LOGGER.warn(exception.getLocalizedMessage(), exception);
                }
            }
            return super.getServerInterface();
        }

        @Override
        public Icon getIcon() {
            if (!StringUtils.isEmpty(extension.icon)) {
                try {
                    return IconLoader.findIcon(extension.icon, extension.getPluginDescriptor().getPluginClassLoader());
                } catch (Exception e) {
                    LOGGER.error("Error while loading custom server icon for server id='" + extension.id + "'.", e);
                }
            }
            return super.getIcon();
        }
    }

    private static LanguageServersRegistry INSTANCE = null;

    public static LanguageServersRegistry getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LanguageServersRegistry();
        }
        return INSTANCE;
    }

    private final Map<String, LanguageServerDefinition> serverDefinitions = new HashMap<>();

    private final List<ContentTypeToLanguageServerDefinition> connections = new ArrayList<>();

    private LanguageServersRegistry() {
        initialize();
    }

    private void initialize() {
        List<LanguageMapping> languageMappings = new ArrayList<>();
        for (ServerExtensionPointBean server : ServerExtensionPointBean.EP_NAME.getExtensions()) {
            if (server.id != null && !server.id.isEmpty()) {
                serverDefinitions.put(server.id, new ExtensionLanguageServerDefinition(server));
            }
        }
        for (LanguageMappingExtensionPointBean extension : LanguageMappingExtensionPointBean.EP_NAME.getExtensions()) {
            Language language = Language.findLanguageByID(extension.language);
            if (language != null) {
                languageMappings.add(new LanguageMapping(language, extension.id, extension.serverId, extension.getDocumentMatcher()));
            }
        }

        for (LanguageMapping mapping : languageMappings) {
            LanguageServerDefinition lsDefinition = serverDefinitions.get(mapping.languageId);
            if (lsDefinition != null) {
                registerAssociation(lsDefinition, mapping);
            } else {
                LOGGER.warn("server '" + mapping.id + "' not available"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    /**
     * @param contentType
     * @return the {@link LanguageServerDefinition}s <strong>directly</strong> associated to the given content-type.
     * This does <strong>not</strong> include the one that match transitively as per content-type hierarchy
     */
    List<ContentTypeToLanguageServerDefinition> findProviderFor(final @NotNull Language contentType) {
        return connections.stream()
                .filter(entry -> contentType.isKindOf(entry.getKey()))
                .collect(Collectors.toList());
    }


    public void registerAssociation(@NotNull LanguageServerDefinition serverDefinition, @Nullable LanguageMapping mapping) {
        @NotNull Language language = mapping.language;
        @Nullable String languageId = mapping.languageId;
        if (languageId != null) {
            serverDefinition.registerAssociation(language, languageId);
        }

        connections.add(new ContentTypeToLanguageServerDefinition(language, serverDefinition, mapping.getDocumentMatcher()));
    }

    /**
     * internal class to capture content-type mappings for language servers
     */
    private static class LanguageMapping {

        @NotNull
        public final String id;
        @NotNull
        public final Language language;
        @Nullable
        public final String languageId;

        private final DocumentMatcher documentMatcher;

        public LanguageMapping(@NotNull Language language, @Nullable String id, @Nullable String languageId, @NotNull DocumentMatcher documentMatcher) {
            this.language = language;
            this.id = id;
            this.languageId = languageId;
            this.documentMatcher = documentMatcher;
        }

        public DocumentMatcher getDocumentMatcher() {
            return documentMatcher;
        }
    }

    /**
     * Returns the language server definition for the given language server id and null otherwise.
     *
     * @param languageServerId the language server id.
     * @return the language server definition for the given language server id and null otherwise.
     */
    public @Nullable LanguageServerDefinition getServerDefinition(@NotNull String languageServerId) {
        return serverDefinitions.get(languageServerId);
    }

    /**
     * Returns the registered server definitions.
     *
     * @return the registered server definitions.
     */
    public Collection<LanguageServerDefinition> getServerDefinitions() {
        return serverDefinitions.values();
    }

    /**
     * Returns true if the language of the file is supported by a language server and false otherwise.
     *
     * @param file the file.
     * @return true if the language of the file is supported by a language server and false otherwise.
     */
    public boolean isLanguageSupported(@Nullable PsiFile file) {
        if (file == null) {
            return false;
        }
        return isLanguageSupported(file.getVirtualFile(), file.getProject());
    }

    /**
     * Returns true if the language of the file is supported by a language server and false otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return true if the language of the file is supported by a language server and false otherwise.
     */
    public boolean isLanguageSupported(@Nullable VirtualFile file, @NotNull Project project) {
        if (file == null) {
            return false;
        }
        Language language = LSPIJUtils.getFileLanguage(file, project);
        if (language == null) {
            return false;
        }
        return connections
                .stream()
                .anyMatch(entry -> language.isKindOf(entry.getKey()));
    }

}


/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEvent;
import com.intellij.openapi.fileTypes.FileTypeListener;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.FileTypeFileViewProviders;
import com.intellij.psi.FileViewProviderFactory;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinitionListener;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * We can't register plain text files of a file type without a language, such as an abstract file type or a file type
 * defined by another plugin, by language or a single file type statically in plugin.xml, so register them dynamically.
 */
public class LSPSemanticTokensStructurelessFileViewProviderRegistrar {

    private static Set<FileType> registeredFileTypes = Collections.synchronizedSet(new HashSet<>());

    private static void registerFileTypes() {
        // Determine the file type associations and filename matchers for language servers
        Set<FileType> lspFileTypes = new HashSet<>();
        Set<FileNameMatcher> lspFilenameMatchers = new HashSet<>();
        for (LanguageServerDefinition languageServerDefinition : LanguageServersRegistry.getInstance().getServerDefinitions()) {
            ContainerUtil.addAllNotNull(lspFileTypes, languageServerDefinition.getFileTypeMappings().keySet());
            for (Pair<List<FileNameMatcher>, String> filenameMatcherMapping : languageServerDefinition.getFilenameMatcherMappings()) {
                ContainerUtil.addAllNotNull(lspFilenameMatchers, filenameMatcherMapping.getFirst());
            }
        }

        // Determine the structureless file types for those file types and filename matchers
        Set<FileType> registerFileTypes = new LinkedHashSet<>();
        for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
            if (isStructureless(fileType)) {
                if (lspFileTypes.contains(fileType)) {
                    registerFileTypes.add(fileType);
                } else {
                    List<FileNameMatcher> filenameMatchers = FileTypeManager.getInstance().getAssociations(fileType);
                    if (ContainerUtil.intersects(lspFilenameMatchers, filenameMatchers)) {
                        registerFileTypes.add(fileType);
                    }
                }
            }
        }

        // Use working copies to avoid potential dirty reads
        Set<FileType> copyOfRegisteredFileTypes = new HashSet<>(registeredFileTypes);
        Set<FileType> workingRegisteredFileTypes = new HashSet<>();

        // Register file types to use our file view provider factory if necessary
        if (!registerFileTypes.isEmpty()) {
            for (FileType registerFileType : registerFileTypes) {
                FileViewProviderFactory currentFactory = FileTypeFileViewProviders.INSTANCE.findSingle(registerFileType);
                if (!(currentFactory instanceof LSPSemanticTokensStructurelessFileViewProviderFactory)) {
                    FileTypeFileViewProviders.INSTANCE.addExplicitExtension(registerFileType, LSPSemanticTokensStructurelessFileViewProviderFactory.INSTANCE);
                }
                workingRegisteredFileTypes.add(registerFileType);
            }
        }

        // Unregister any obsolete registrations
        Collection<FileType> unregisterFileTypes = ContainerUtil.subtract(copyOfRegisteredFileTypes, workingRegisteredFileTypes);
        if (!unregisterFileTypes.isEmpty()) {
            for (FileType unregisterFileType : unregisterFileTypes) {
                FileTypeFileViewProviders.INSTANCE.removeExplicitExtension(unregisterFileType, LSPSemanticTokensStructurelessFileViewProviderFactory.INSTANCE);
            }
        }

        // If changed, update our retained file type/factory associations
        if (!copyOfRegisteredFileTypes.equals(workingRegisteredFileTypes)) {
            registeredFileTypes = workingRegisteredFileTypes;
        }
    }

    /**
     * Whether the PSI of a file of the given type is a single plain text element, so our view provider can give it
     * elements for its semantic tokens. That is the case for an abstract file type, and for a file type without a
     * language, such as one a plugin defines for a language server, unless the file type has a view provider factory
     * of its own.
     *
     * @param fileType the file type
     * @return true if our view provider factory can be registered for the file type; otherwise false
     */
    private static boolean isStructureless(@NotNull FileType fileType) {
        if (fileType instanceof AbstractFileType) {
            return true;
        }
        if (fileType instanceof LanguageFileType || fileType.isBinary()) {
            return false;
        }
        return ContainerUtil.and(FileTypeFileViewProviders.INSTANCE.allForFileType(fileType),
                factory -> factory instanceof LSPSemanticTokensStructurelessFileViewProviderFactory);
    }

    // LISTENERS

    // Register structureless file types when a project is opened
    public static class ProjectOpenedListener implements ProjectActivity {
        @Override
        @Nullable
        public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            // We can't register this listener in plugin.xml, so register it when the first project is opened
            LanguageServerDefinitionsChangedListener.registerOnce();
            registerFileTypes();
            return null;
        }

    }

    // Register structureless file types when file types change
    public static class FileTypesChangedListener implements FileTypeListener {
        @Override
        public void fileTypesChanged(@NotNull FileTypeEvent event) {
            registerFileTypes();
        }
    }

    // Register structureless file types when language server definitions change
    private static class LanguageServerDefinitionsChangedListener implements LanguageServerDefinitionListener {
        private static volatile boolean addedLanguageServerDefinitionListener = false;

        private static void registerOnce() {
            if (!addedLanguageServerDefinitionListener) {
                synchronized (LSPSemanticTokensStructurelessFileViewProviderRegistrar.class) {
                    if (!addedLanguageServerDefinitionListener) {
                        LanguageServersRegistry.getInstance().addLanguageServerDefinitionListener(new LanguageServerDefinitionsChangedListener());
                        addedLanguageServerDefinitionListener = true;
                    }
                }
            }
        }

        @Override
        public void handleAdded(@NotNull LanguageServerAddedEvent event) {
            registerFileTypes();
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerRemovedEvent event) {
            registerFileTypes();
        }

        @Override
        public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            registerFileTypes();
        }
    }
}

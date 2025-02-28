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
 * We can't register plain text/abstract file type files by language or a single file type statically in plugin.xml, so
 * register them dynamically.
 */
public class LSPSemanticTokensStructurelessFileViewProviderRegistrar {

    private static Set<FileType> registeredAbstractFileTypes = Collections.synchronizedSet(new HashSet<>());

    private static void registerAbstractFileTypes() {
        // Determine the file type associations and filename matchers for language servers
        Set<FileType> lspFileTypes = new HashSet<>();
        Set<FileNameMatcher> lspFilenameMatchers = new HashSet<>();
        for (LanguageServerDefinition languageServerDefinition : LanguageServersRegistry.getInstance().getServerDefinitions()) {
            ContainerUtil.addAllNotNull(lspFileTypes, languageServerDefinition.getFileTypeMappings().keySet());
            for (Pair<List<FileNameMatcher>, String> filenameMatcherMapping : languageServerDefinition.getFilenameMatcherMappings()) {
                ContainerUtil.addAllNotNull(lspFilenameMatchers, filenameMatcherMapping.getFirst());
            }
        }

        // Determine the abstract file types for those file types and filename matchers
        Set<FileType> registerFileTypes = new LinkedHashSet<>();
        for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
            if (fileType instanceof AbstractFileType abstractFileType) {
                if (lspFileTypes.contains(abstractFileType)) {
                    registerFileTypes.add(abstractFileType);
                } else {
                    List<FileNameMatcher> filenameMatchers = FileTypeManager.getInstance().getAssociations(abstractFileType);
                    if (ContainerUtil.intersects(lspFilenameMatchers, filenameMatchers)) {
                        registerFileTypes.add(abstractFileType);
                    }
                }
            }
        }

        // Use working copies to avoid potential dirty reads
        Set<FileType> copyOfRegisteredAbstractFileTypes = new HashSet<>(registeredAbstractFileTypes);
        Set<FileType> workingRegisteredAbstractFileTypes = new HashSet<>();

        // Register file types to use our file view provider factory if necessary
        if (!registerFileTypes.isEmpty()) {
            for (FileType registerFileType : registerFileTypes) {
                FileViewProviderFactory currentFactory = FileTypeFileViewProviders.INSTANCE.findSingle(registerFileType);
                if (!(currentFactory instanceof LSPSemanticTokensStructurelessFileViewProviderFactory)) {
                    FileTypeFileViewProviders.INSTANCE.addExplicitExtension(registerFileType, LSPSemanticTokensStructurelessFileViewProviderFactory.INSTANCE);
                }
                workingRegisteredAbstractFileTypes.add(registerFileType);
            }
        }

        // Unregister any obsolete registrations
        Collection<FileType> unregisterFileTypes = ContainerUtil.subtract(copyOfRegisteredAbstractFileTypes, workingRegisteredAbstractFileTypes);
        if (!unregisterFileTypes.isEmpty()) {
            for (FileType unregisterFileType : unregisterFileTypes) {
                FileTypeFileViewProviders.INSTANCE.removeExplicitExtension(unregisterFileType, LSPSemanticTokensStructurelessFileViewProviderFactory.INSTANCE);
            }
        }

        // If changed, update our retained file type/factory associations
        if (!copyOfRegisteredAbstractFileTypes.equals(workingRegisteredAbstractFileTypes)) {
            registeredAbstractFileTypes = workingRegisteredAbstractFileTypes;
        }
    }

    // LISTENERS

    // Register abstract file types when a project is opened
    public static class ProjectOpenedListener implements ProjectActivity {
        @Override
        @Nullable
        public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
            // We can't register this listener in plugin.xml, so register it when the first project is opened
            LanguageServerDefinitionsChangedListener.registerOnce();
            registerAbstractFileTypes();
            return null;
        }

    }

    // Register abstract file types when file types change
    public static class FileTypesChangedListener implements FileTypeListener {
        @Override
        public void fileTypesChanged(@NotNull FileTypeEvent event) {
            registerAbstractFileTypes();
        }
    }

    // Register abstract file types when language server definitions change
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
            registerAbstractFileTypes();
        }

        @Override
        public void handleRemoved(@NotNull LanguageServerRemovedEvent event) {
            registerAbstractFileTypes();
        }

        @Override
        public void handleChanged(@NotNull LanguageServerChangedEvent event) {
            registerAbstractFileTypes();
        }
    }
}

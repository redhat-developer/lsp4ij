/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.server.definition;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Language server definition listener API.
 */
public interface LanguageServerDefinitionListener {

    class LanguageServerAddedEvent {

        public final Collection<LanguageServerDefinition> serverDefinitions;

        public LanguageServerAddedEvent(@NotNull Collection<LanguageServerDefinition> serverDefinitions) {
            this.serverDefinitions = serverDefinitions;
        }
    }

    class LanguageServerRemovedEvent {

        public final Collection<LanguageServerDefinition> serverDefinitions;

        public LanguageServerRemovedEvent(@NotNull Collection<LanguageServerDefinition> serverDefinitions) {
            this.serverDefinitions = serverDefinitions;
        }
    }

    class LanguageServerChangedEvent {

        public final LanguageServerDefinition serverDefinition;

        public final boolean nameChanged;

        public final boolean commandChanged;

        public final boolean mappingsChanged;
        public final boolean configurationChanged;
        public final boolean initializationOptionsContentChanged;

        public LanguageServerChangedEvent(@NotNull LanguageServerDefinition serverDefinition, boolean nameChanged, boolean commandChanged, boolean mappingsChanged, boolean configurationContentChanged, boolean initializationOptionsContentChanged) {
            this.serverDefinition = serverDefinition;
            this.nameChanged = nameChanged;
            this.commandChanged = commandChanged;
            this.mappingsChanged = mappingsChanged;
            this.configurationChanged = configurationContentChanged;
            this.initializationOptionsContentChanged = initializationOptionsContentChanged;
        }
    }
    void handleAdded(@NotNull LanguageServerDefinitionListener.LanguageServerAddedEvent event);

    void handleRemoved(@NotNull LanguageServerDefinitionListener.LanguageServerRemovedEvent event);

    void handleChanged(@NotNull LanguageServerDefinitionListener.LanguageServerChangedEvent event);
}

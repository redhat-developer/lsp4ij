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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Debug Adapter Protocol (DAP) descriptor factory listener API.
 */
public interface DebugAdapterServerListener {

    abstract class DebugAdapterDescriptorFactoryEvent {
        DebugAdapterDescriptorFactoryEvent() {

        }
    }

    class AddedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final Collection<DebugAdapterServerDefinition> serverDefinitions;

        public AddedEvent(@NotNull Collection<DebugAdapterServerDefinition> serverDefinitions) {
            this.serverDefinitions = serverDefinitions;
        }
    }

    class RemovedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final Collection<DebugAdapterServerDefinition> serverDefinitions;

        public RemovedEvent(@NotNull Collection<DebugAdapterServerDefinition> serverDefinitions) {
            this.serverDefinitions = serverDefinitions;
        }
    }

    class ChangedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final DebugAdapterServerDefinition serverDefinition;

        public final boolean nameChanged;
        public final boolean commandChanged;
        public final boolean userEnvironmentVariablesChanged;
        public final boolean includeSystemEnvironmentVariablesChanged;
        public final boolean waitForTimeoutChanged;
        public final boolean debugServerReadyPatternChanged;
        public final boolean mappingsChanged;
        public final boolean launchConfigurationsContentChanged;
        private final boolean attachAddressChanged;
        private final boolean attachPortChanged;

        public ChangedEvent(@NotNull DebugAdapterServerDefinition serverDefinition,
                            boolean nameChanged,
                            boolean commandChanged,
                            boolean userEnvironmentVariablesChanged,
                            boolean includeSystemEnvironmentVariablesChanged,
                            boolean waitForTimeoutChanged,
                            boolean debugServerReadyPatternChanged,
                            boolean mappingsChanged,
                            boolean launchConfigurationsContentChanged,
                            boolean attachAddressChanged,
                            boolean attachPortChanged) {
            this.serverDefinition = serverDefinition;
            this.nameChanged = nameChanged;
            this.commandChanged = commandChanged;
            this.userEnvironmentVariablesChanged = userEnvironmentVariablesChanged;
            this.includeSystemEnvironmentVariablesChanged = includeSystemEnvironmentVariablesChanged;
            this.waitForTimeoutChanged = waitForTimeoutChanged;
            this.debugServerReadyPatternChanged = debugServerReadyPatternChanged;
            this.mappingsChanged = mappingsChanged;
            this.launchConfigurationsContentChanged = launchConfigurationsContentChanged;
            this.attachAddressChanged = attachAddressChanged;
            this.attachPortChanged = attachPortChanged;
        }
    }

    void handleAdded(@NotNull DebugAdapterServerListener.AddedEvent event);

    void handleRemoved(@NotNull DebugAdapterServerListener.RemovedEvent event);

    void handleChanged(@NotNull DebugAdapterServerListener.ChangedEvent event);
}

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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Debug Adapter Protocol (DAP) descriptor factory listener API.
 */
public interface DebugAdapterDescriptorFactoryListener {

    abstract class DebugAdapterDescriptorFactoryEvent {
        DebugAdapterDescriptorFactoryEvent() {

        }
    }

    class DebugAdapterDescriptorFactoryAddedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final Collection<DebugAdapterDescriptorFactory> serverDescriptors;

        public DebugAdapterDescriptorFactoryAddedEvent(@NotNull Collection<DebugAdapterDescriptorFactory> serverDefinitions) {
            this.serverDescriptors = serverDefinitions;
        }
    }

    class DebugAdapterDescriptorFactoryRemovedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final Collection<DebugAdapterDescriptorFactory> serverDefinitions;

        public DebugAdapterDescriptorFactoryRemovedEvent(@NotNull Collection<DebugAdapterDescriptorFactory> serverDefinitions) {
            this.serverDefinitions = serverDefinitions;
        }
    }

    class DebugAdapterDescriptorFactoryChangedEvent extends DebugAdapterDescriptorFactoryEvent {

        public final DebugAdapterDescriptorFactory descriptorFactory;

        public final boolean nameChanged;
        public final boolean commandChanged;
        public final boolean userEnvironmentVariablesChanged;
        public final boolean includeSystemEnvironmentVariablesChanged;
        public final boolean waitForTimeoutChanged;
        public final boolean waitForTraceChanged;
        public final boolean mappingsChanged;
        public final boolean launchConfigurationsContentChanged;

        public DebugAdapterDescriptorFactoryChangedEvent(@NotNull DebugAdapterDescriptorFactory descriptorFactory,
                                                         boolean nameChanged,
                                                         boolean commandChanged,
                                                         boolean userEnvironmentVariablesChanged,
                                                         boolean includeSystemEnvironmentVariablesChanged,
                                                         boolean waitForTimeoutChanged,
                                                         boolean waitForTraceChanged,
                                                         boolean mappingsChanged,
                                                         boolean launchConfigurationsContentChanged) {
            this.descriptorFactory = descriptorFactory;
            this.nameChanged = nameChanged;
            this.commandChanged = commandChanged;
            this.userEnvironmentVariablesChanged = userEnvironmentVariablesChanged;
            this.includeSystemEnvironmentVariablesChanged = includeSystemEnvironmentVariablesChanged;
            this.waitForTimeoutChanged = waitForTimeoutChanged;
            this.waitForTraceChanged = waitForTraceChanged;
            this.mappingsChanged = mappingsChanged;
            this.launchConfigurationsContentChanged = launchConfigurationsContentChanged;
        }
    }

    void handleAdded(@NotNull DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryAddedEvent event);

    void handleRemoved(@NotNull DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryRemovedEvent event);

    void handleChanged(@NotNull DebugAdapterDescriptorFactoryListener.DebugAdapterDescriptorFactoryChangedEvent event);
}

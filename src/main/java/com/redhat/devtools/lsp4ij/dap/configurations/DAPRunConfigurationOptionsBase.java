/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.openapi.components.StoredProperty;
import com.redhat.devtools.lsp4ij.dap.configurations.options.ServerTraceConfigurable;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;

/**
 * Debug Adapter Protocol (DAP) run configuration options base.
 */
public class DAPRunConfigurationOptionsBase  extends RunConfigurationOptions implements ServerTraceConfigurable {

    private final StoredProperty<String> serverTrace = string(ServerTrace.getDefaultValue().name())
            .provideDelegate(this, "serverTrace");

    @Override
    public ServerTrace getServerTrace() {
        return ServerTrace.get(serverTrace.getValue(this));
    }

    @Override
    public void setServerTrace(ServerTrace serverTrace) {
        this.serverTrace.setValue(this, serverTrace.name());
    }

}

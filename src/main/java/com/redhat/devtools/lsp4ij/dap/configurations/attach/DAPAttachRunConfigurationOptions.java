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
package com.redhat.devtools.lsp4ij.dap.configurations.attach;

import com.intellij.openapi.components.StoredProperty;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptionsBase;
import com.redhat.devtools.lsp4ij.dap.configurations.options.AttachConfigurable;
import org.jetbrains.annotations.Nullable;

/**
 * DAP run configuration options for "attach" mode.
 */
public class DAPAttachRunConfigurationOptions extends DAPRunConfigurationOptionsBase implements AttachConfigurable {

    private final StoredProperty<String> attachAddress = string("")
            .provideDelegate(this, "attachAddress");

    private final StoredProperty<String> attachPort = string("")
            .provideDelegate(this, "attachPort");

    @Override
    public @Nullable String getAttachAddress() {
        return attachAddress.getValue(this);
    }

    @Override
    public void setAttachAddress(@Nullable String attachAddress) {
        this.attachAddress.setValue(this, attachAddress);
    }

    @Override
    public @Nullable String getAttachPort() {
        return attachPort.getValue(this);
    }

    @Override
    public void setAttachPort(@Nullable String attachPort) {
        this.attachPort.setValue(this, attachPort);
    }

}

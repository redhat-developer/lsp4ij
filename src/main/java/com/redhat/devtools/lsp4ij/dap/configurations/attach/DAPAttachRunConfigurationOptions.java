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
 * <p>
 * LSP4IJ does not register this attach type itself; it is a base class for downstream plugins that
 * provide their own DAP "attach" run configuration (for example {@code QuteRunConfiguration} in
 * intellij-quarkus). The {@code forwardNativeLineBreakpoints} option is inherited from
 * {@link DAPRunConfigurationOptionsBase} (via {@code ForwardNativeLineBreakpointsConfigurable}), so
 * attach-based configurations support breakpoint forwarding too, and
 * {@link com.redhat.devtools.lsp4ij.dap.configurations.attach.DAPAttachSettingsEditor} exposes it.
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

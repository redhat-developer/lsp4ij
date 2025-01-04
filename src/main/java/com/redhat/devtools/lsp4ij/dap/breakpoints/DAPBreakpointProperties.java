package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DAPBreakpointProperties extends XBreakpointProperties<DAPBreakpointProperties> {

    @Override
    public @Nullable DAPBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DAPBreakpointProperties state) {

    }
}

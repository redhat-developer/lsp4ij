package com.redhat.devtools.lsp4ij.dap.features;

import org.jetbrains.annotations.Nullable;

public record ServerReadyConfig(@Nullable String waitForTrace, @Nullable Integer waitForTimeout) {

}

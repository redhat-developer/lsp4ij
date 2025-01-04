package com.redhat.devtools.lsp4ij.dap;

import org.jetbrains.annotations.NotNull;

public enum DebuggingType {

    LAUNCH,
    ATTACH;

    @NotNull
    public static DebuggingType get(String value) {
        try {
            return DebuggingType.valueOf(value.toUpperCase());
        }
        catch(Exception e) {
            return DebuggingType.LAUNCH;
        }
    }
}

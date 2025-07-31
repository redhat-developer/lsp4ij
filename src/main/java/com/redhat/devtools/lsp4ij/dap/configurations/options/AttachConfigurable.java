package com.redhat.devtools.lsp4ij.dap.configurations.options;

import org.jetbrains.annotations.Nullable;

public interface AttachConfigurable {

    @Nullable String getAttachAddress();

    void setAttachAddress(@Nullable String attachAddress);

    @Nullable String getAttachPort();

    void setAttachPort(@Nullable String attachPort);

}

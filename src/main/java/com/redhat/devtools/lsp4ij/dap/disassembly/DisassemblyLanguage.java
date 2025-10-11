package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.lang.Language;

public class DisassemblyLanguage extends Language {
    public static final DisassemblyLanguage INSTANCE = new DisassemblyLanguage();

    private DisassemblyLanguage() {
        super("Disassembly");
    }
}

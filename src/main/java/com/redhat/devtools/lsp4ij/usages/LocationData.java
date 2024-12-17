package com.redhat.devtools.lsp4ij.usages;

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.Location;

public record LocationData(Location location, LanguageServerItem languageServer) {
}

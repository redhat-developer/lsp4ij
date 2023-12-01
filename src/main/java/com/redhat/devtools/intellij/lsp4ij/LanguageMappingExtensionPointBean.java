package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageMappingExtensionPointBean extends BaseKeyedLazyInstance<DocumentMatcher> {

    private static final DocumentMatcher DEFAULT_DOCUMENT_MATCHER = (file,project) -> true;

    public static final ExtensionPointName<LanguageMappingExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.lsp4ij.languageMapping");

    @Attribute("id")
    public String id;

    @Attribute("language")
    public String language;

    @Attribute("serverId")
    public String serverId;

    @Attribute("documentMatcher")
    public String documentMatcher;

    public @NotNull DocumentMatcher getDocumentMatcher() {
        try {
            return super.getInstance();
        }
        catch(Exception e) {
            return DEFAULT_DOCUMENT_MATCHER;
        }
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return documentMatcher;
    }
}

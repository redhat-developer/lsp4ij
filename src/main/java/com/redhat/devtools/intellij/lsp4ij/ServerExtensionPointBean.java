package com.redhat.devtools.intellij.lsp4ij;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.redhat.devtools.intellij.lsp4ij.server.StreamConnectionProvider;
import org.jetbrains.annotations.Nullable;

public class ServerExtensionPointBean extends BaseKeyedLazyInstance<StreamConnectionProvider> {
    public static final ExtensionPointName<ServerExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.intellij.lsp4ij.server");

    @Attribute("id")
    public String id;

    @Attribute("label")
    public String label;

    @Tag("description")
    public String description;

    @Attribute("class")
    public String serverImpl;
    private Class<?> serverImplClass;

    @Attribute("clientImpl")
    public String clientImpl;
    private Class clientClass;

    @Attribute("serverInterface")
    public String serverInterface;
    private Class serverClass;

    /**
     *  Valid values are <code>project</code> and <code>application</code><br/>
     *  When <code>project</code> scope is selected, the implementation of {@link StreamConnectionProvider} requires a
     *  constructor with a single {@link com.intellij.openapi.project.Project} parameter
     */
    @Attribute("scope")
    public String scope;

    @Attribute("singleton")
    public boolean singleton;

    @Attribute("supportsLightEdit")
    public boolean supportsLightEdit;

    @Attribute("lastDocumentDisconnectedTimeout")
    public Integer lastDocumentDisconnectedTimeout;

    public Class getClientImpl() throws ClassNotFoundException {
        if (clientClass == null) {
            clientClass = getPluginDescriptor().getPluginClassLoader().loadClass(clientImpl);
        }
        return clientClass;
    }

    public Class getServerImpl() throws ClassNotFoundException {
        if (serverImplClass == null) {
            serverImplClass = getPluginDescriptor().getPluginClassLoader().loadClass(serverImpl);
        }
        return serverImplClass;
    }

    public Class getServerInterface() throws ClassNotFoundException {
        if (serverClass == null) {
            serverClass = getPluginDescriptor().getPluginClassLoader().loadClass(serverInterface);
        }
        return serverClass;
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return serverImpl;
    }
}

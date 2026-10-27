/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.LoadedSourcesResponse;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.ModulesResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The loaded sources and modules are asked for only when the DAP server says it reports them, and a failed request
 * shows nothing.
 */
public class DAPClientLoadedSourcesTest extends BasePlatformTestCase {

    private final List<String> asked = new ArrayList<>();

    public void testAsksOnlyADebugAdapterThatReportsThem() {
        var server = server(false);
        assertNull(DAPClient.getLoadedSources(server, new Capabilities()).join());
        assertNull(DAPClient.getModules(server, new Capabilities()).join());
        assertEmpty(asked);

        var capabilities = new Capabilities();
        capabilities.setSupportsLoadedSourcesRequest(true);
        capabilities.setSupportsModulesRequest(true);
        var sources = DAPClient.getLoadedSources(server, capabilities).join();
        var modules = DAPClient.getModules(server, capabilities).join();
        assertEquals(List.of("/work/a.x"), List.of(sources).stream().map(Source::getPath).toList());
        assertEquals(List.of("app"), List.of(modules).stream().map(Module::getName).toList());
        assertEquals(List.of("loadedSources", "modules"), asked);
    }

    public void testAFailedRequestShowsNothing() {
        var capabilities = new Capabilities();
        capabilities.setSupportsLoadedSourcesRequest(true);
        capabilities.setSupportsModulesRequest(true);
        var server = server(true);
        assertNull(DAPClient.getLoadedSources(server, capabilities).join());
        assertNull(DAPClient.getModules(server, capabilities).join());
    }

    private IDebugProtocolServer server(boolean fails) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "loadedSources" -> {
                    asked.add("loadedSources");
                    if (fails) {
                        return CompletableFuture.failedFuture(new IllegalStateException("no sources"));
                    }
                    var source = new Source();
                    source.setPath("/work/a.x");
                    var response = new LoadedSourcesResponse();
                    response.setSources(new Source[]{source});
                    return CompletableFuture.completedFuture(response);
                }
                case "modules" -> {
                    asked.add("modules");
                    if (fails) {
                        return CompletableFuture.failedFuture(new IllegalStateException("no modules"));
                    }
                    var module = new Module();
                    module.setId("app");
                    module.setName("app");
                    var response = new ModulesResponse();
                    response.setModules(new Module[]{module});
                    return CompletableFuture.completedFuture(response);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                default -> throw new AssertionError(method.getName());
            }
        });
    }
}

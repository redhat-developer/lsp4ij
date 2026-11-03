/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A user-defined language server receives the null members of the JSON settings given to it: in the initialization
 * options of initialize, in the settings of workspace/didChangeConfiguration and in the reply to
 * workspace/configuration (issue #465).
 */
public class UserDefinedNullSettingsTest extends LSPCodeInsightFixtureTestCase {

    private static final String SETTINGS = "{\"a\": null, \"b\": \"x\"}";

    private UserDefinedLanguageServerDefinition definition;

    public UserDefinedNullSettingsTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(),
                null,
                "Test server",
                null,
                null,
                "test-server",
                null,
                Map.of(),
                false,
                SETTINGS,
                true,
                null,
                SETTINGS,
                null,
                null,
                null,
                null) {
            @Override
            public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
                // The mock server answers in place of the process, with the user-defined initialization options
                var userDefined = super.createConnectionProvider(project);
                return new MockConnectionProvider(null) {
                    @Override
                    public Object getInitializationOptions(@Nullable VirtualFile rootUri) {
                        return userDefined.getInitializationOptions(rootUri);
                    }
                };
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.bar"), null)));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), definition);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testInitializationOptionsKeepTheirNull() throws Exception {
        start();
        assertEquals(settings(), MockLanguageServer.INSTANCE.getInitializeParams().getInitializationOptions());
    }

    public void testDidChangeConfigurationSettingsKeepTheirNull() throws Exception {
        start();
        // The user-defined language client sends its settings once the server has started
        var changes = MockLanguageServer.INSTANCE.getWorkspaceService().getConfigurationChanges();
        PlatformTestUtil.waitWithEventsDispatching("the settings must be sent", () -> !changes.isEmpty(), 30);
        assertEquals(settings(), changes.get(0).getSettings());
    }

    public void testConfigurationReplyKeepsTheNull() throws Exception {
        start();
        var reply = MockLanguageServer.INSTANCE.getRemoteProxies().get(0)
                .configuration(new ConfigurationParams(List.of(new ConfigurationItem())));
        PlatformTestUtil.waitWithEventsDispatching("workspace/configuration reply", reply::isDone, 30);
        assertEquals(List.of(settings()), reply.join());
    }

    private void start() throws Exception {
        var file = myFixture.configureByText("main.bar", "");
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
    }

    private static JsonElement settings() {
        return JsonParser.parseString(SETTINGS);
    }
}

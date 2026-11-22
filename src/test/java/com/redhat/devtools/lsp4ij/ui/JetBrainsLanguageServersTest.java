/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * LSP4IJ reads the language servers of IntelliJ's own LSP client (2026.3 and later) from the state that IntelliJ
 * stores, through the class loader of its settings page, and reads none when it can't (#1933).
 */
public class JetBrainsLanguageServersTest extends BasePlatformTestCase {

    /**
     * The state of the settings service of IntelliJ IDEA 2026.3 (263.7307), as {@code XmlSerializer} gives it.
     */
    private static final String STATE = """
            <LspIntegrationSettingsImpl>
              <pluginServers />
              <servers>
                <server name="probe" enabled="true" executablePath="/usr/bin/python3" communicationMode="STDIO" socketPort="0">
                  <option name="envVars">
                    <EnvironmentVariablesDataOptions />
                  </option>
                  <arguments>server.py</arguments>
                  <filePatterns>*.lsptest; Makefile</filePatterns>
                  <initializationOptions />
                </server>
                <server name="off" enabled="false" executablePath="" communicationMode="SOCKET" socketPort="0">
                  <option name="envVars">
                    <EnvironmentVariablesDataOptions />
                  </option>
                  <arguments />
                  <filePatterns>*.off</filePatterns>
                  <initializationOptions />
                </server>
              </servers>
            </LspIntegrationSettingsImpl>""";

    public void testTheServersAreReadFromTheStateThatIntelliJStores() throws Exception {
        var servers = JetBrainsLanguageServers.parse(JDOMUtil.load(STATE));

        assertEquals(List.of(
                new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest", "Makefile")),
                new JetBrainsLanguageServers.Server("off", false, List.of("*.off"))), servers);
    }

    public void testAServerIsEnabledWhenItsStateLeavesItOut() throws Exception {
        var servers = JetBrainsLanguageServers.parse(JDOMUtil.load("""
                <state><servers><server name="default"><filePatterns>*.x;;</filePatterns></server></servers></state>"""));

        assertEquals(List.of(new JetBrainsLanguageServers.Server("default", true, List.of("*.x"))), servers);
    }

    public void testAServerMatchesTheFileNamesOfItsPatterns() {
        var server = new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest", "Makefile"));

        assertTrue(server.matches("a.lsptest"));
        assertTrue(server.matches("Makefile"));
        assertFalse(server.matches("a.lsptest.txt"));
        assertFalse(server.matches("a.txt"));
    }

    public void testTheServersAreReadThroughTheSettingsPage() {
        registerSettingsPage();
        var settings = new FakeSettingsImpl();
        settings.servers.add(new FakeServer("probe", true, "*.lsptest"));
        settings.servers.add(new FakeServer("off", false, "*.off"));
        ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), FakeSettings.class, settings, getTestRootDisposable());

        assertEquals(List.of(
                        new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest")),
                        new JetBrainsLanguageServers.Server("off", false, List.of("*.off"))),
                JetBrainsLanguageServers.getServers(getProject(), FakeSettings.class.getName()));
    }

    /**
     * IntelliJ IDEA 2026.3 and later bundle the page, as on CI's leg on 2026.3: LSP4IJ reads the servers of its real
     * settings service. Before 2026.3, there's no page, and no servers.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testTheServersOfTheRealSettingsPageAreRead() throws Exception {
        var page = Configurable.PROJECT_CONFIGURABLE.getExtensions(getProject()).stream()
                .filter(extension -> JetBrainsLanguageServers.CONFIGURABLE_ID.equals(extension.id))
                .findFirst();
        if (page.isEmpty()) {
            assertEmpty(JetBrainsLanguageServers.getServers(getProject()));
            return;
        }
        var settingsClass = Class.forName(JetBrainsLanguageServers.SETTINGS_CLASS, true, page.get().getPluginDescriptor().getClassLoader());
        var settings = (PersistentStateComponent) getProject().getService(settingsClass);
        Element saved = XmlSerializer.serialize(settings.getState());
        try {
            // No file of the tests matches these servers, so IntelliJ starts none of them
            settings.loadState(XmlSerializer.deserialize(JDOMUtil.load("""
                    <LspIntegrationSettingsImpl>
                      <servers>
                        <server name="probe" executablePath="/no/such/server">
                          <filePatterns>*.jetbrains-probe; Probefile</filePatterns>
                        </server>
                        <server name="off" enabled="false">
                          <filePatterns>*.jetbrains-off</filePatterns>
                        </server>
                      </servers>
                    </LspIntegrationSettingsImpl>"""), settings.getClass()));

            assertEquals(List.of(
                            new JetBrainsLanguageServers.Server("probe", true, List.of("*.jetbrains-probe", "Probefile")),
                            new JetBrainsLanguageServers.Server("off", false, List.of("*.jetbrains-off"))),
                    JetBrainsLanguageServers.getServers(getProject()));
        } finally {
            settings.loadState(XmlSerializer.deserialize(saved, settings.getClass()));
        }
    }

    public void testNoServersWithoutTheSettingsPage() {
        var settings = new FakeSettingsImpl();
        settings.servers.add(new FakeServer("probe", true, "*.lsptest"));
        ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), FakeSettings.class, settings, getTestRootDisposable());
        // As before 2026.3, which has no page
        setSettingsPages(List.of());

        assertEmpty(JetBrainsLanguageServers.getServers(getProject(), FakeSettings.class.getName()));
    }

    public void testNoServersWhenTheSettingsCannotBeRead() {
        registerSettingsPage();

        // A settings class which isn't there, as in IntelliJ without its LSP module
        assertEmpty(JetBrainsLanguageServers.getServers(getProject(), "com.example.NoSuchSettings"));
        // A settings service which isn't registered
        assertEmpty(JetBrainsLanguageServers.getServers(getProject(), UnregisteredSettings.class.getName()));
        // A settings service without a state
        ServiceContainerUtil.registerOrReplaceServiceInstance(getProject(), StatelessSettings.class, new StatelessSettings() {
        }, getTestRootDisposable());
        assertEmpty(JetBrainsLanguageServers.getServers(getProject(), StatelessSettings.class.getName()));
    }

    public interface UnregisteredSettings {
    }

    public interface StatelessSettings {
    }

    private void registerSettingsPage() {
        // As IntelliJ creates it from the plugin.xml of its plugin
        var page = new ConfigurableEP<Configurable>() {
        };
        page.id = JetBrainsLanguageServers.CONFIGURABLE_ID;
        page.instanceClass = FakeConfigurable.class.getName();
        page.setPluginDescriptor(new DefaultPluginDescriptor(PluginId.getId("com.intellij.lsp.ui"), getClass().getClassLoader()));
        // In place of the real page, from 2026.3 on
        setSettingsPages(List.of(page));
    }

    private void setSettingsPages(@NotNull List<ConfigurableEP<Configurable>> pages) {
        var projectPages = new ExtensionPointName<ConfigurableEP<Configurable>>(Configurable.PROJECT_CONFIGURABLE.getName());
        ExtensionTestUtil.maskExtensions(projectPages, pages, getTestRootDisposable(), false, getProject());
    }

    /**
     * Stands for {@code LspIntegrationSettings}, the settings service of the page.
     */
    public interface FakeSettings {
    }

    /**
     * Stands for {@code LspIntegrationSettingsImpl}, whose state is itself, with the same serialization.
     */
    public static final class FakeSettingsImpl implements FakeSettings, PersistentStateComponent<FakeSettingsImpl> {

        @XCollection(style = XCollection.Style.v2)
        public List<FakeServer> servers = new ArrayList<>();

        @Override
        public FakeSettingsImpl getState() {
            return this;
        }

        @Override
        public void loadState(@NotNull FakeSettingsImpl state) {
            servers = state.servers;
        }
    }

    /**
     * Stands for {@code LspServerConfiguration}, with the same serialization.
     */
    @Tag("server")
    public static final class FakeServer {

        @Attribute("name")
        public String name = "";

        @Attribute("enabled")
        public boolean enabled = true;

        @Tag("filePatterns")
        public String filePatterns = "";

        @SuppressWarnings("unused")
        public FakeServer() {
        }

        FakeServer(String name, boolean enabled, String filePatterns) {
            this.name = name;
            this.enabled = enabled;
            this.filePatterns = filePatterns;
        }
    }

    public static final class FakeConfigurable implements Configurable {

        @Override
        public String getDisplayName() {
            return "Language Servers";
        }

        @Override
        public @Nullable JComponent createComponent() {
            return null;
        }

        @Override
        public boolean isModified() {
            return false;
        }

        @Override
        public void apply() {
        }
    }
}

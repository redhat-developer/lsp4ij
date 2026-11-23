/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerInstaller;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerInstaller;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An installer whose run fails ends its installation as before: the notification says that the installation failed,
 * and the language server starts, as a run can report a failure for a server that it installed. But the installation
 * of a user-defined language server isn't counted as done, so that its first start in the next IDE session checks it
 * again, and installs the server if the check fails.
 */
public class FailedInstallationTest extends BasePlatformTestCase {

    private static final int TIMEOUT = 30;

    // A task of the installers of the tests, which passes or fails as the test says for its name
    private static final String TASK = "testInstallerTask";

    private static final String INSTALLER = """
            {
              "name": "Test server",
              "executeOnStartServer": false,
              "check": {"testInstallerTask": {"name": "check"}},
              "run": {"testInstallerTask": {"name": "run"}}
            }""";

    // The result of the tasks, by name, and the names of the tasks that ran, in order
    private final Map<String, Boolean> results = new ConcurrentHashMap<>();
    private final List<String> runs = new CopyOnWriteArrayList<>();
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    private UserDefinedLanguageServerDefinition definition;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ServerInstallerManager.getInstance().registerFactory(TASK, (json, descriptor) -> {
            String name = json.get("name").getAsString();
            return new InstallerTask(null, name, null, null, descriptor) {
                @Override
                public boolean run(@NotNull InstallerContext context) {
                    runs.add(name);
                    return results.getOrDefault(name, false);
                }
            };
        });
        ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
                .subscribe(Notifications.TOPIC, new Notifications() {
                    @Override
                    public void notify(@NotNull Notification notification) {
                        notifications.add(notification);
                    }
                });
        definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(),
                null,
                "Test server",
                null,
                null,
                "test-server",
                null,
                Map.of(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                INSTALLER,
                null);
        // As a language server created from a template, whose settings say that its installation wasn't done
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.failedinstall"), null)));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
            ServerInstallerManager.getInstance().unregisterFactory(TASK);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testAFailedRunStartsTheLanguageServerAsBeforeButIsNotDone() throws Exception {
        results.put("run", false);
        var installer = new UserDefinedLanguageServerInstaller(definition);

        var installation = install(installer);

        // As before: the installation ends, and the language server starts
        assertEquals(ServerInstallationStatus.INSTALLED, installation.get());
        assertEquals(ServerInstallationStatus.INSTALLED, installer.getStatus());
        assertFalse("An installation whose run failed isn't done", settings().isInstallAlreadyDone());
        assertEquals(List.of("check", "run"), runs);
        // As before: the notification says that the installation failed
        PlatformTestUtil.waitWithEventsDispatching("The failure must be notified", () -> !notifications.isEmpty(), TIMEOUT);
        assertSize(1, notifications);
        assertEquals(NotificationType.ERROR, notifications.get(0).getType());
        assertEquals("Installation of <code>Test server</code> failed.", notifications.get(0).getTitle());
    }

    public void testTheNextStartInstallsAgainAfterAFailedRun() throws Exception {
        results.put("run", false);
        install(new UserDefinedLanguageServerInstaller(definition));

        // The next start of the IDE, whose installer of the language server is new, as its status
        results.put("run", true);
        var installer = new UserDefinedLanguageServerInstaller(definition);
        var installation = install(installer);

        assertEquals(ServerInstallationStatus.INSTALLED, installation.get());
        assertEquals("The installer must check and run again", List.of("check", "run", "check", "run"), runs);
        assertTrue("A successful installation is done", settings().isInstallAlreadyDone());
    }

    public void testTheNextStartOnlyChecksAfterAFailedRunThatInstalledTheServer() throws Exception {
        // A run that reports a failure for a server that it installed, as a command which prints a warning on its
        // standard error
        results.put("run", false);
        install(new UserDefinedLanguageServerInstaller(definition));

        // The next start of the IDE, whose check finds the server
        results.put("check", true);
        var installation = install(new UserDefinedLanguageServerInstaller(definition));

        assertEquals(ServerInstallationStatus.INSTALLED, installation.get());
        assertEquals("The installer must only check", List.of("check", "run", "check"), runs);
        assertTrue("An installation whose check passes is done", settings().isInstallAlreadyDone());
    }

    public void testAServerInstalledBeforeIsNotInstalledAgain() throws Exception {
        results.put("run", true);
        assertEquals(ServerInstallationStatus.INSTALLED, install(new UserDefinedLanguageServerInstaller(definition)).get());
        assertTrue(settings().isInstallAlreadyDone());

        // The next start of the IDE
        var installation = new UserDefinedLanguageServerInstaller(definition).checkInstallation();

        assertEquals(ServerInstallationStatus.INSTALLED, installation.getNow(null));
        assertEquals("The installer must not run again", List.of("check", "run"), runs);
    }

    public void testAFailedRunOfADebugAdapterEndsItsInstallationAsBefore() throws Exception {
        results.put("run", false);
        var debugAdapter = new UserDefinedDebugAdapterServerDefinition(UUID.randomUUID().toString(),
                "Test debug adapter",
                "test-debug-adapter",
                List.of(),
                List.of());
        debugAdapter.setInstallerConfiguration(INSTALLER);
        var installer = new UserDefinedDebugAdapterServerInstaller(debugAdapter);

        var installation = install(installer);

        // As before: the installation ends, and the debug session starts. A debug adapter checks its installation at
        // its first debug run in each IDE session
        assertEquals(ServerInstallationStatus.INSTALLED, installation.get());
        assertEquals(ServerInstallationStatus.INSTALLED, installer.getStatus());
        assertEquals(List.of("check", "run"), runs);
    }

    public void testAnInstallationThatThrewKeepsTheLanguageServerOffWithoutAnError() {
        var features = new LSPClientFeatures();
        features.setServerInstaller(new FailedInstaller());

        assertFalse(features.isEnabled(new LightVirtualFile("main.failedinstall")));
        assertFalse(features.isWaitingForServerInstallation());
    }

    private static @NotNull CompletableFuture<ServerInstallationStatus> install(@NotNull ServerInstaller installer) {
        var installation = installer.checkInstallation();
        PlatformTestUtil.waitWithEventsDispatching("The installation must end", installation::isDone, TIMEOUT);
        return installation;
    }

    private @NotNull UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings() {
        var settings = UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(definition.getId());
        assertNotNull(settings);
        return settings;
    }

    /**
     * An installer whose installation threw an exception, which fails the check of the installation.
     */
    private static final class FailedInstaller implements ServerInstaller {

        @Override
        public @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation() {
            return CompletableFuture.failedFuture(new IllegalStateException("The installation failed"));
        }

        @Override
        public @NotNull CompletableFuture<ServerInstallationStatus> checkInstallation(@NotNull ServerInstallationContext context) {
            return checkInstallation();
        }

        @Override
        public @NotNull ServerInstallationStatus getStatus() {
            return ServerInstallationStatus.NOT_INSTALLED;
        }

        @Override
        public void reset() {
        }

        @Override
        public void registerConsoleProvider(@NotNull ConsoleProvider consoleProvider) {
        }

        @Override
        public void unregisterConsoleProvider(@NotNull ConsoleProvider consoleProvider) {
        }
    }
}

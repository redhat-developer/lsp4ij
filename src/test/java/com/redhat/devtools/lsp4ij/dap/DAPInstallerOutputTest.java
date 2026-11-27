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
package com.redhat.devtools.lsp4ij.dap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.ide.actions.ShowLogAction;
import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerInstaller;
import com.redhat.devtools.lsp4ij.dap.installation.DeclarativeDebugAdapterServerInstaller;
import com.redhat.devtools.lsp4ij.installation.CommandLineUpdater;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.installation.definition.PrintOutput;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the installer of a debug adapter prints at the start of a debug session stays in sight: the console of the start,
 * which the debug runner removes once the session starts, gives it to the {@code Installer} tab of the session, and the
 * notification which says that the installation failed shows that tab and the IDE's log, which has each step (#2296).
 */
public class DAPInstallerOutputTest extends HeavyPlatformTestCase {

    private static final int TIMEOUT_SECONDS = 60;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The directory of the project, in which the commands run, which isn't on disk until it is created
        getOrCreateProjectBaseDir();
    }

    public void testTheInstallerTabOfADebugSessionShowsTheInstallationWhichRanAtItsStart() throws Exception {
        // The check fails, so the installation runs, and the console of the start shows it after it cleared the check
        var installer = new TestInstaller(ServerInstallerManager.getInstance().loadInstaller(installer(
                "Test debug adapter",
                exec("Check the debug adapter", "checked-at-the-start-of-the-session", "exit:1"),
                exec("Install the debug adapter", "installed-at-the-start-of-the-session"))));
        // As the debug runner starts a session whose debug adapter isn't installed yet
        var startConsole = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();
        Disposer.register(getTestRootDisposable(), startConsole);
        var environment = new UserDataHolderBase();
        installer.registerConsoleProvider(DAPInstallerOutput.record(startConsole, getProject(), environment));
        assertEquals(ServerInstallationStatus.INSTALLED, check(installer));

        // The session starts, and its layout creates its Installer tab
        var installerOutput = DAPInstallerOutput.of(environment);
        assertNotNull("The output of the installation is kept for the session", installerOutput);
        String text = installerTabText(installerOutput);

        // The check and the installation, with what each command printed
        assertTrue(text, text.contains("- Step: Check the debug adapter"));
        assertTrue(text, text.contains("\nchecked-at-the-start-of-the-session\n"));
        assertTrue(text, text.contains("Exit code: 1"));
        assertTrue(text, text.contains("- Step: Install the debug adapter"));
        assertTrue(text, text.contains("\ninstalled-at-the-start-of-the-session\n"));
        assertTrue(text, text.contains("Exit code: 0"));
        assertTrue(text, text.contains("Working directory: "));
    }

    public void testTheInstallerTabOfADebugSessionWithoutAnInstallationAtItsStartIsEmpty() throws Exception {
        // As when the debug adapter was installed already
        assertEquals("", installerTabText(null));
    }

    public void testTheNotificationOfAFailedInstallationShowsTheInstallerTabAndTheLog() throws Exception {
        var notification = installUserDefined("Failing debug adapter", exec("Check", "exit:1"),
                exec("Install", "installation-failed", "exit:2"));

        assertEquals("Installation of <code>Failing debug adapter</code> failed.", notification.getTitle());
        List<String> actions = notification.getActions().stream().map(AnAction::getTemplateText).toList();
        List<String> expected = ShowLogAction.isSupported()
                ? List.of("Show Installer Tab", ShowLogAction.notificationAction().getTemplateText())
                // As on a Linux without a file manager
                : List.of("Show Installer Tab");
        assertEquals(expected, actions);
    }

    public void testTheNotificationOfASuccessfulInstallationHasNoAction() throws Exception {
        var notification = installUserDefined("Installed debug adapter", exec("Check", "exit:1"),
                exec("Install", "installed"));

        assertEquals("Installation of <code>Installed debug adapter</code> completed successfully.",
                notification.getTitle());
        assertEmpty(notification.getActions());
    }

    /**
     * Installs the user-defined debug adapter of the given installer for the project, as a debug session of the project
     * starts it, and returns the notification which says how the installation ended.
     */
    private @NotNull Notification installUserDefined(@NotNull String name,
                                                     @NotNull JsonObject check,
                                                     @NotNull JsonObject run) throws Exception {
        var server = new UserDefinedDebugAdapterServerDefinition("dap-installer-output-test", name, "unused",
                List.of(), List.of());
        server.setInstallerConfiguration(installer(name, check, run).toString());
        List<Notification> notifications = new CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                notifications.add(notification);
            }
        });

        check(new UserDefinedDebugAdapterServerInstaller(server));

        assertSize(1, notifications);
        return notifications.get(0);
    }

    /**
     * Checks the installation with the given installer, as a DAP run configuration of the project starts it, and
     * returns how it ended.
     */
    private @NotNull ServerInstallationStatus check(@NotNull ServerInstaller installer) throws Exception {
        var installation = DAPDebugRunner.checkInstallation(installer, getProject());
        PlatformTestUtil.waitWithEventsDispatching("The check of the installation must end within " + TIMEOUT_SECONDS
                + " s", installation::isDone, TIMEOUT_SECONDS);
        return installation.get();
    }

    /**
     * Creates the Installer tab of a debug session, with what the installer printed at its start, and returns what its
     * console shows.
     */
    private @NotNull String installerTabText(@Nullable DAPInstallerOutput installerOutput) {
        FormBuilder builder = FormBuilder.createFormBuilder();
        var installerPanel = DAPDebugProcess.createInstallerPanel(builder, null, new CommandLineUpdater() {
            @Override
            public @Nullable String getCommandLine() {
                return null;
            }

            @Override
            public void setCommandLine(String commandLine) {
            }
        }, getProject(), installerOutput);
        Disposer.register(getTestRootDisposable(), installerPanel);
        var console = UIUtil.findComponentOfType(builder.getPanel(), ConsoleViewImpl.class);
        assertNotNull("The Installer tab has a console", console);
        // The console shows what it got once it flushes it
        console.flushDeferredText();
        return console.getText();
    }

    private static @NotNull JsonObject installer(@NotNull String name, @NotNull JsonObject check,
                                                 @NotNull JsonObject run) {
        var installer = new JsonObject();
        installer.addProperty("name", name);
        installer.add("check", check);
        installer.add("run", run);
        return installer;
    }

    /**
     * Returns the exec task of the given name, which runs {@link PrintOutput} with the given arguments, and ignores its
     * standard error, where the JVM can say that it picked up JAVA_TOOL_OPTIONS.
     */
    private static @NotNull JsonObject exec(@NotNull String name, @NotNull String... args) {
        var command = new JsonArray();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(PathManager.getJarPathForClass(PrintOutput.class));
        command.add(PrintOutput.class.getName());
        for (String arg : args) {
            command.add(arg);
        }
        var exec = new JsonObject();
        exec.addProperty("name", name);
        exec.add("command", command);
        exec.addProperty("ignoreStderr", true);
        var task = new JsonObject();
        task.add("exec", exec);
        return task;
    }

    /**
     * The installer of a debug adapter, which checks its installation at its first debug session.
     */
    private static final class TestInstaller extends DeclarativeDebugAdapterServerInstaller {

        private final @NotNull ServerInstallerDescriptor descriptor;

        TestInstaller(@NotNull ServerInstallerDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        protected @Nullable ServerInstallerDescriptor getServerInstallerDescriptor() {
            return descriptor;
        }

        @Override
        protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
            return true;
        }
    }
}

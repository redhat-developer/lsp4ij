/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.redhat.devtools.lsp4ij.installation.definition.InstallerContext.InstallerAction.CHECK;
import static com.redhat.devtools.lsp4ij.installation.definition.InstallerContext.InstallerAction.CHECK_AND_RUN;
import static com.redhat.devtools.lsp4ij.installation.definition.InstallerContext.InstallerAction.RUN;

/**
 * The links of the Installer tab, Check Installation, Run Installation and Check / Run Installation, say that there is
 * nothing to do when the installer has no task for them, instead of doing nothing and saying that the installation
 * completed successfully (#1229).
 */
public class ServerInstallerManagerMissingTaskTest extends BasePlatformTestCase {

    private static final String TASK = "{\"fileExists\": {\"file\": \"/no/such/server\"}}";
    private static final String WITHOUT_TASKS = "{\"name\": \"my-server\"}";
    private static final String WITH_CHECK_ONLY = "{\"name\": \"my-server\", \"check\": " + TASK + "}";
    private static final String WITH_RUN_ONLY = "{\"name\": \"my-server\", \"run\": " + TASK + "}";

    public void testACheckNeedsACheckAndARunNeedsARun() {
        assertFalse(hasTaskToExecute(WITHOUT_TASKS, CHECK));
        assertFalse(hasTaskToExecute(WITHOUT_TASKS, RUN));
        assertFalse(hasTaskToExecute(WITHOUT_TASKS, CHECK_AND_RUN));

        assertTrue(hasTaskToExecute(WITH_CHECK_ONLY, CHECK));
        assertFalse(hasTaskToExecute(WITH_CHECK_ONLY, RUN));
        assertFalse(hasTaskToExecute(WITH_CHECK_ONLY, CHECK_AND_RUN));

        assertFalse(hasTaskToExecute(WITH_RUN_ONLY, CHECK));
        assertTrue(hasTaskToExecute(WITH_RUN_ONLY, RUN));
        assertTrue(hasTaskToExecute(WITH_RUN_ONLY, CHECK_AND_RUN));
    }

    public void testRunInstallationOfAnInstallerWithoutRunSaysThereIsNothingToInstall() {
        var notifications = install(WITH_CHECK_ONLY, RUN);

        assertSize(1, notifications);
        assertEquals(NotificationType.WARNING, notifications.get(0).getType());
        assertEquals("Nothing to install for <code>my-server</code>.", notifications.get(0).getTitle());
        assertEquals("The installer has no <code>run</code>.", notifications.get(0).getContent());
    }

    public void testCheckAndRunInstallationOfAnInstallerWithoutRunSaysThereIsNothingToInstall() {
        var notifications = install(WITH_CHECK_ONLY, CHECK_AND_RUN);

        assertSize(1, notifications);
        assertEquals(NotificationType.WARNING, notifications.get(0).getType());
        assertEquals("Nothing to install for <code>my-server</code>.", notifications.get(0).getTitle());
    }

    public void testCheckInstallationOfAnInstallerWithoutCheckSaysThereIsNothingToCheck() {
        var notifications = install(WITH_RUN_ONLY, CHECK);

        assertSize(1, notifications);
        assertEquals(NotificationType.WARNING, notifications.get(0).getType());
        assertEquals("Nothing to check for <code>my-server</code>.", notifications.get(0).getTitle());
        assertEquals("The installer has no <code>check</code>.", notifications.get(0).getContent());
    }

    private static boolean hasTaskToExecute(@NotNull String json, @NotNull InstallerContext.InstallerAction action) {
        var installer = ServerInstallerManager.getInstance().loadInstaller(json);
        return ServerInstallerManager.hasTaskToExecute(installer, action);
    }

    /**
     * Installs as a link of the Installer tab does, and returns the notifications it gives at once.
     */
    private @NotNull List<Notification> install(@NotNull String json, @NotNull InstallerContext.InstallerAction action) {
        List<Notification> notifications = new CopyOnWriteArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(Notifications.TOPIC, new Notifications() {
            @Override
            public void notify(@NotNull Notification notification) {
                notifications.add(notification);
            }
        });
        ServerInstallerManager.getInstance().install(json, new InstallerContext(getProject(), action));
        return List.copyOf(notifications);
    }
}

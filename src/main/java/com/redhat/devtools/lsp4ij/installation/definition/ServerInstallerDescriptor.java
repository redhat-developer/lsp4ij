/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes an installable server by declaring the installer tasks associated with it.
 * <p>
 * A server installer descriptor holds:
 * <ul>
 *     <li>a server installer {@code name},</li>
 *     <li>an {@link InstallerTaskRegistry} containing all known task implementations,</li>
 *     <li>an optional {@code check} task used to verify installation,</li>
 *     <li>an optional {@code run} task used to perform the installation or launch process.</li>
 * </ul>
 * This class is used during server installation workflows to identify and execute the appropriate tasks.
 */
public class ServerInstallerDescriptor {

    private final @NotNull String name;
    private final boolean executeOnStartServer;
    private final @NotNull InstallerTaskRegistry installerTaskRegistry;
    private @Nullable InstallerTask check;
    private @Nullable InstallerTask run;

    public ServerInstallerDescriptor(@NotNull String name,
                                     boolean executeOnStartServer,
                                     @NotNull InstallerTaskRegistry installerTaskRegistry) {
        this.name = name;
        this.executeOnStartServer = executeOnStartServer;
        this.installerTaskRegistry = installerTaskRegistry;
    }

    public @NotNull String getName() {
        return name;
    }

    public boolean isExecuteOnStartServer() {
        return executeOnStartServer;
    }

    public @Nullable InstallerTask getCheck() {
        return check;
    }

    public @Nullable InstallerTask getRun() {
        return run;
    }

    public void setCheck(@Nullable InstallerTask check) {
        this.check = check;
    }

    public void setRun(@Nullable InstallerTask run) {
        this.run = run;
    }

    public @NotNull InstallerTaskRegistry getStepActionRegistry() {
        return installerTaskRegistry;
    }
}

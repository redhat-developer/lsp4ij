/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.openapi.progress.ProgressIndicator;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Declarative language server installer.
 */
public abstract class DeclarativeLanguageServerInstaller extends LanguageServerInstallerBase {

    @Override
    public CompletableFuture<ServerInstallationStatus> checkInstallation() {
        if (!isInstallFutureInitialized()) {
            ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
            if (serverInstallerDescriptor == null || !canExecute(serverInstallerDescriptor)) {
                return CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
            }
        }
        return super.checkInstallation();
    }

    @Override
    protected boolean checkServerInstalled(@NotNull ProgressIndicator indicator) throws Exception {
        ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
        if (serverInstallerDescriptor == null) {
            return true;
        }
        var context = createInstallerContext(InstallerContext.InstallerAction.CHECK, indicator);
        context.setShowNotification(false);
        return ServerInstallerManager.getInstance().install(serverInstallerDescriptor, context);
    }

    protected boolean canExecute(@NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        return serverInstallerDescriptor.isExecuteOnStartServer();
    }

    @Override
    protected void install(@NotNull ProgressIndicator indicator) throws Exception {
        ServerInstallerDescriptor serverInstallerDescriptor = getServerInstallerDescriptor();
        if (serverInstallerDescriptor == null) {
            return;
        }
        var context = createInstallerContext(InstallerContext.InstallerAction.RUN, indicator);
        ServerInstallerManager.getInstance().install(serverInstallerDescriptor, context);
    }

    protected @NotNull InstallerContext createInstallerContext(@NotNull InstallerContext.InstallerAction action,
                                                               @NotNull ProgressIndicator indicator) {
        var context = new InstallerContext(getProject(), action);
        context.setProgressIndicator(indicator);
        return context;
    }


    protected abstract @Nullable ServerInstallerDescriptor getServerInstallerDescriptor();

}

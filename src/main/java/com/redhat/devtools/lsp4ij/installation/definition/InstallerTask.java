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

import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Base class for installer task.
 */
public abstract class InstallerTask {

    private final @Nullable String id;
    private final @Nullable String name;
    private final @Nullable InstallerTask onFail;
    private final @Nullable InstallerTask onSuccess;
    private final @NotNull ServerInstallerDescriptor serverInstallerDeclaration;

    public InstallerTask(@Nullable String id,
                         @Nullable String name,
                         @Nullable InstallerTask onFail,
                         @Nullable InstallerTask onSuccess,
                         @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        this.id = id;
        this.name = name;
        this.onFail = onFail;
        this.onSuccess = onSuccess;
        this.serverInstallerDeclaration = serverInstallerDeclaration;
    }

    public @Nullable String getId() {
        return id;
    }

    public @Nullable String getName() {
        return name;
    }

    public @NotNull ServerInstallerDescriptor getServerInstallerDeclaration() {
        return serverInstallerDeclaration;
    }

    public @Nullable InstallerTask getOnFail() {
        return onFail;
    }

    public @Nullable InstallerTask getOnSuccess() {
        return onSuccess;
    }

    public boolean execute(@NotNull InstallerContext context) {
        // Display step name.
        String message = "\n- Step: " + getName();
        context.print(message, ConsoleViewContentType.LOG_INFO_OUTPUT);
        if(run(context)) {
            if (onSuccess != null) {
                return onSuccess.execute(context);
            }
            return true;
        }
        if (onFail != null) {
            return onFail.execute(context);
        }
        return false;
    }

    public abstract boolean run(@NotNull InstallerContext context);
}

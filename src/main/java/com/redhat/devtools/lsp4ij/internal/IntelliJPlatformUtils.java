/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal;

import com.intellij.openapi.application.ApplicationInfo;
import org.eclipse.lsp4j.ClientInfo;

/**
 * IntelliJ Platform utilities.
 */
public class IntelliJPlatformUtils {

    private static final boolean IN_DEV_MODE = Boolean.getBoolean("idea.plugin.in.sandbox.mode");

    private static ClientInfo INTELLIJ_CLIENT_INFO;

    /**
     * Returns true if we are in dev mode and false otherwise.
     *
     * @return true if we are in dev mode and false otherwise.
     */
    public static boolean isDevMode() {
        return IN_DEV_MODE;
    }

    /**
     * Returns the LSP client info for Intellij.
     *
     * @return the LSP client info for Intellij.
     */
    public static ClientInfo getClientInfo() {
        if (INTELLIJ_CLIENT_INFO != null) {
            return INTELLIJ_CLIENT_INFO;
        }
        INTELLIJ_CLIENT_INFO = getOrCreateClientInfo();
        return INTELLIJ_CLIENT_INFO;
    }

    private synchronized static ClientInfo getOrCreateClientInfo() {
        if (INTELLIJ_CLIENT_INFO != null) {
            return INTELLIJ_CLIENT_INFO;
        }
        ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
        String clientName = applicationInfo.getFullApplicationName();
        String versionName = applicationInfo.getVersionName();
        String buildNumber = applicationInfo.getBuild().asString();

        String intellijVersion = versionName + " (build " + buildNumber + ")";
        return new ClientInfo(clientName, intellijVersion);
    }
}

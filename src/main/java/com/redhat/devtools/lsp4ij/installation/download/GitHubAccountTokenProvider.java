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
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the token of a GitHub account of the IDE, as the accounts of IntelliJ's GitHub plugin, for the GitHub API of a
 * {@code github} download (#2188).
 * <p>
 * An application service, which {@code plugin-github.xml} registers when the GitHub plugin is enabled. Without it,
 * {@link #getInstance()} returns null, and the classes of the GitHub plugin are never loaded.
 */
@ApiStatus.Internal
@FunctionalInterface
public interface GitHubAccountTokenProvider {

    /**
     * Returns the provider of the GitHub plugin, or null if the GitHub plugin isn't enabled.
     *
     * @return the provider of the GitHub plugin, or null.
     */
    static @Nullable GitHubAccountTokenProvider getInstance() {
        return ApplicationManager.getApplication().getService(GitHubAccountTokenProvider.class);
    }

    /**
     * Returns the token of a GitHub account of the IDE on the server of the given GitHub API, as
     * {@code https://api.github.com}, or null if there is none. The token of an account goes to its own server only.
     * It reads the Password Safe: not on the EDT.
     *
     * @param apiUrl the URL of the GitHub API, without a trailing slash.
     * @return the token of a GitHub account of the IDE on that server, or null.
     */
    @RequiresBackgroundThread
    @Nullable AccountToken findToken(@NotNull String apiUrl);

    /**
     * The token of a GitHub account of the IDE. Its string doesn't show the token.
     *
     * @param login the login of the account on GitHub, which the console names.
     * @param token the token.
     */
    record AccountToken(@NotNull String login, @NotNull String token) {

        @Override
        public @NotNull String toString() {
            return "AccountToken[" + login + "]";
        }
    }
}

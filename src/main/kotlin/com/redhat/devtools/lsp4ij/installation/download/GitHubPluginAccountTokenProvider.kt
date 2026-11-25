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
package com.redhat.devtools.lsp4ij.installation.download

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Computable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.redhat.devtools.lsp4ij.installation.download.GitHubAccountTokenProvider.AccountToken
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

/**
 * Gives the token of a GitHub account of IntelliJ's GitHub plugin, from *Settings | Version Control | GitHub* (#2188).
 *
 * Only `plugin-github.xml`, which the IDE reads when the GitHub plugin is enabled, registers it: no other class of LSP4IJ
 * refers to the classes of the GitHub plugin.
 *
 * It uses the application service `GHAccountManager` of the GitHub plugin, the same from 2024.2 to 2026.3, with its
 * accounts and `findCredentials` of IntelliJ's collaboration tools, which reads the token of an account from the Password
 * Safe. `GHAccountManager` is public in 2024.2 and 2025.3, and internal in 2026.3, where the GitHub plugin has no public
 * way to read the token of an account: `GHCompatibilityUtil.getOrRequestToken` is internal too, and asks the user to
 * log in when there is no token, and `GHAccountsUtil` requires the EDT and gives no token.
 */
internal class GitHubPluginAccountTokenProvider : GitHubAccountTokenProvider {

    @RequiresBackgroundThread
    override fun findToken(apiUrl: String): AccountToken? {
        val accountManager = service<GHAccountManager>()
        // The first account of the settings on that server: the token of an account goes to its own server only, as
        // the token of a GitHub Enterprise account never goes to github.com
        val account = accountManager.accountsState.value.firstOrNull { it.server.toApiUrl() == apiUrl } ?: return null
        val token = runBlockingWithProgress { accountManager.findCredentials(account) }
        return if (token.isNullOrBlank()) null else AccountToken(account.name, token)
    }

    /**
     * Runs the given action, which the progress of the installation cancels. Without a progress, as in a test, it
     * gets one of its own: `runBlockingMaybeCancellable`, which allows none, is internal before 2026.3.
     */
    private fun <T> runBlockingWithProgress(action: suspend CoroutineScope.() -> T): T =
        if (ProgressManager.getGlobalProgressIndicator() != null) {
            runBlockingCancellable(action)
        } else {
            ProgressManager.getInstance().runProcess(Computable { runBlockingCancellable(action) },
                EmptyProgressIndicator())
        }
}

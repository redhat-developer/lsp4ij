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
import com.intellij.testFramework.TestApplicationManager
import com.redhat.devtools.lsp4ij.installation.download.GitHubAccountTokenProvider.AccountToken
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The GitHub plugin, which the tests load, registers the provider of `plugin-github.xml`, which gives the token of a
 * GitHub account of the IDE, as *Settings | Version Control | GitHub* has it, for the GitHub API of its server only
 * (#2188). The test application keeps the accounts and the Password Safe in memory.
 */
class GitHubPluginAccountTokenProviderTest {

    companion object {
        private const val TOKEN = "gho_test0123456789abcdefACCOUNT"
        private const val GITHUB_API = "https://api.github.com"
        private const val ENTERPRISE_API = "https://ghe.example.com/api/v3"

        @JvmStatic
        @BeforeClass
        fun initPlatform() {
            TestApplicationManager.getInstance()
        }
    }

    @Test
    fun testTheGitHubPluginRegistersTheProvider() {
        val provider = GitHubAccountTokenProvider.getInstance()
        assertTrue("plugin-github.xml must register the provider of the GitHub plugin: $provider",
            provider is GitHubPluginAccountTokenProvider)
    }

    @Test
    fun testThereIsNoTokenWithoutAnAccount() {
        assertNull(provider().findToken(GITHUB_API))
    }

    @Test
    fun testTheTokenOfAGitHubAccountGoesToTheGitHubApiOnly() {
        withAccount(GithubAccount("octocat", GithubServerPath.DEFAULT_SERVER, "lsp4ij-test-github")) {
            assertEquals(AccountToken("octocat", TOKEN), provider().findToken(GITHUB_API))
            assertNull("the token of a github.com account must not go to another server",
                provider().findToken(ENTERPRISE_API))
        }
    }

    @Test
    fun testTheTokenOfAGitHubEnterpriseAccountGoesToItsServerOnly() {
        withAccount(GithubAccount("monalisa", GithubServerPath("ghe.example.com"), "lsp4ij-test-enterprise")) {
            assertNull("the token of a GitHub Enterprise account must not go to github.com",
                provider().findToken(GITHUB_API))
            assertEquals(AccountToken("monalisa", TOKEN), provider().findToken(ENTERPRISE_API))
        }
    }

    private fun provider(): GitHubAccountTokenProvider =
        GitHubAccountTokenProvider.getInstance() ?: throw AssertionError("The GitHub plugin registers no provider")

    /**
     * Runs the given action with the given account and its token in the GitHub settings, in a background thread, as an
     * installer reads them.
     */
    private fun withAccount(account: GithubAccount, action: () -> Unit) {
        val accountManager = service<GHAccountManager>()
        await("add the account ${account.name}") { accountManager.updateAccount(account, TOKEN) }
        try {
            action()
        } finally {
            await("remove the account ${account.name}") { accountManager.removeAccount(account) }
        }
    }

    private fun await(what: String, action: suspend () -> Unit) {
        try {
            runBlocking { withTimeout(30.seconds) { action() } }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("The GitHub plugin didn't $what in 30 seconds", e)
        }
    }
}

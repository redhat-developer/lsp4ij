// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.redhat.devtools.lsp4ij.fixtures

import com.intellij.codeInsight.inline.completion.InlineCompletionHandler
import com.intellij.codeInsight.inline.completion.InlineCompletionLifecycleTestDSL
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.redhat.devtools.lsp4ij.InlineCompletionTestCase
import com.redhat.devtools.lsp4ij.JSONUtils
import com.redhat.devtools.lsp4ij.LanguageServerManager
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.features.inlineCompletion.LSPInlineCompletionProvider
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings
import org.eclipse.lsp4j.InlineCompletionItem
import org.eclipse.lsp4j.InlineCompletionList
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.annotations.ApiStatus
import java.util.*
import kotlin.time.Duration

/**
 * Base test case for inline completion tests.
 *
 * Copied from IntelliJ Platform and enhanced with LSP server registration.
 */
abstract class LSPInlineCompletionTestCase(vararg val fileNamePatterns: String) : InlineCompletionTestCase() {

    var languageId: String? = null
}

/**
 * DSL wrapper that provides server registration methods
 */
class LSPInlineCompletionTestDSL(
    private val dsl: InlineCompletionLifecycleTestDSL,
    private val fixture: CodeInsightTestFixture,
    private val definition: MockLanguageServerDefinition,
    private val mockServer: MockLanguageServer,
    private val patterns: List<String>,
    private val langId: String?,
    private val inlineCompletion: Either<List<InlineCompletionItem>, InlineCompletionList>
) {

    suspend fun registerServer() {
        coroutineToIndicator {

            // Register the LSP inline completion provider for tests
            InlineCompletionHandler.registerTestHandler(
                LSPInlineCompletionProvider(),
            )

            // Register server
            val mappings = listOf(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, langId))
            LanguageServersRegistry.getInstance().addServerDefinition(fixture.project, definition, mappings)

            // Force start the language server
            val options = LanguageServerManager.StartOptions().setForceStart(true)
            LanguageServerManager.getInstance(fixture.project).start(definition, options)

            // Configure mock
            mockServer.setTimeToProceedQueries(200)
            mockServer.setInlineCompletion(inlineCompletion)
        }
    }

    suspend fun unregisterServer() {
        coroutineToIndicator {
            InlineCompletionHandler.unRegisterTestHandler()

            mockServer.waitBeforeTearDown()
            LanguageServersRegistry.getInstance().removeServerDefinition(fixture.project, definition)
        }
    }

    // Delegate all DSL methods to the wrapped instance
    suspend fun callInlineCompletion() = dsl.callInlineCompletion()
    suspend fun typeChar(char: Char) = dsl.typeChar(char)
    suspend fun delay() = dsl.delay()
    suspend fun assertInlineRender(expected: String) = dsl.assertInlineRender(expected)
    suspend fun nextVariant() = dsl.nextVariant()
    suspend fun insert() = dsl.insert()
    suspend fun assertFileContent(expected: String) = dsl.assertFileContent(expected)
    suspend fun assertInlineHidden() = dsl.assertInlineHidden()
}

/**
 * Extension function for testInlineCompletion that wraps the IntelliJ DSL.
 *
 * This is **Experimental API**.
 *
 * If you use this DSL to write a test in JUnit3 or JUnit4 test classes, **please set `runInDispatchThread` to `false``, otherwise, you'll
 * get a deadlock.
 *
 * @param json the LSP inline completion response as JSON
 * @param timeout the timeout for the test
 * @param block the test block
 */
@ApiStatus.Experimental
fun LSPInlineCompletionTestCase.testInlineCompletion(
    fixture: CodeInsightTestFixture,
    json: String,
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    block: suspend LSPInlineCompletionTestDSL.() -> Unit
) {
    // Capture the test case context
    val testCase = this
    val patterns = testCase.fileNamePatterns
    val langId = testCase.languageId

    // Create server and parse JSON before entering the DSL
    val testId = UUID.randomUUID().toString()
    val definition = MockLanguageServerDefinition(testId, false)
    val mockServer = definition.server

    // Parse JSON - support both List and InlineCompletionList formats
    val inlineCompletion = try {
        // Try parsing as InlineCompletionList first (with "items" field)
        val list = JSONUtils.getLsp4jGson().fromJson(json, InlineCompletionList::class.java)
        Either.forRight<List<InlineCompletionItem>, InlineCompletionList>(list)
    } catch (e: Exception) {
        // Fallback: parse as direct array
        val items = JSONUtils.getLsp4jGson().fromJson(json, Array<InlineCompletionItem>::class.java)
        Either.forLeft<List<InlineCompletionItem>, InlineCompletionList>(items.toList())
    }

    timeoutRunBlocking(timeout) {
        val dsl = InlineCompletionLifecycleTestDSL(fixture)
        val wrapper = LSPInlineCompletionTestDSL(
            dsl,
            fixture,
            definition,
            mockServer,
            patterns.toList(),
            langId,
            inlineCompletion
        )
        // Execute the test block - tests must call registerServer() and unregisterServer()
        wrapper.block()
    }
}


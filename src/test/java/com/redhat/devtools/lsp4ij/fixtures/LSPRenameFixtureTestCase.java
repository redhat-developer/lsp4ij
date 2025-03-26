/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.features.rename.LSPRenameUnitTestMode;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class test case to test LSP 'textDocument/prepareRename', 'textDocument/rename' features.
 */
public abstract class LSPRenameFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    protected static final String PREPARE_RENAME_NO_RESULT = "{}";

    public LSPRenameFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Test LSP rename.
     *
     * @param fileName                  the file name used to match registered language servers.
     * @param text                      the editor content text.
     * @param jsonPrepareRenameResponse the emulated JSON prepare rename response and null otherwise.
     * @param expectedPlaceholder       the expected placeholder and null otherwise.
     * @param jsonRenameResponse        the emulated rename JSON rename response WorkspaceEdit and null otherwise.
     * @param expectedRenamedText       the expected renamed text.
     */
    protected void assertRename(@NotNull String fileName,
                                @NotNull String text,
                                @Nullable String jsonPrepareRenameResponse,
                                @NotNull String expectedPlaceholder,
                                @NotNull String jsonRenameResponse,
                                @NotNull String expectedRenamedText) {
        assertRename(fileName,
                text,
                jsonPrepareRenameResponse,
                expectedPlaceholder,
                jsonRenameResponse,
                expectedRenamedText,
                null,
                true);
    }

    /**
     * Test LSP rename with error by waiting for starting language server.
     *
     * @param fileName                  the file name used to match registered language servers.
     * @param text                      the editor content text.
     * @param jsonPrepareRenameResponse the emulated JSON prepare rename response and null otherwise.
     * @param expectedPlaceholder       the expected placeholder.
     * @param jsonRenameResponse        the emulated rename JSON rename response WorkspaceEdit and null otherwise.
     * @param expectedError             the expected error.
     */
    protected void assertRenameWithError(@NotNull String fileName,
                                         @NotNull String text,
                                         @Nullable String jsonPrepareRenameResponse,
                                         @Nullable String expectedPlaceholder,
                                         @Nullable String jsonRenameResponse,
                                         @NotNull String expectedError) {
        assertRenameWithError(fileName,
                text,
                jsonPrepareRenameResponse,
                expectedPlaceholder,
                jsonRenameResponse,
                expectedError,
                true);
    }

    /**
     * Test LSP rename with error.
     *
     * @param fileName                  the file name used to match registered language servers.
     * @param text                      the editor content text.
     * @param jsonPrepareRenameResponse the emulated JSON prepare rename response and null otherwise.
     * @param expectedPlaceholder       the expected placeholder.
     * @param jsonRenameResponse        the emulated rename JSON rename response WorkspaceEdit and null otherwise.
     * @param expectedError             the expected error.
     * @param waitFor                   wait for some ms to take some times to start the language server.
     */
    protected void assertRenameWithError(@NotNull String fileName,
                                         @NotNull String text,
                                         @Nullable String jsonPrepareRenameResponse,
                                         @Nullable String expectedPlaceholder,
                                         @Nullable String jsonRenameResponse,
                                         @NotNull String expectedError,
                                         boolean waitFor) {
        assertRename(fileName,
                text,
                jsonPrepareRenameResponse,
                expectedPlaceholder,
                jsonRenameResponse,
                null,
                expectedError,
                waitFor);
    }

    /**
     * Test LSP rename.
     *
     * @param fileName                  the file name used to match registered language servers.
     * @param text                      the editor content text.
     * @param jsonPrepareRenameResponse the emulated JSON prepare rename response and null otherwise.
     * @param expectedPlaceholder       the expected placeholder and null otherwise.
     * @param jsonRenameResponse        the emulated rename JSON rename response WorkspaceEdit and null otherwise.
     * @param expectedRenamedText       the expected renamed text and null otherwise.
     * @param expectedError             the expected error and null otherwise.
     * @param waitFor                   wait for some ms to take some times to start the language server.
     */
    private void assertRename(@NotNull String fileName,
                              @NotNull String text,
                              @Nullable String jsonPrepareRenameResponse,
                              @Nullable String expectedPlaceholder,
                              @Nullable String jsonRenameResponse,
                              @Nullable String expectedRenamedText,
                              @Nullable String expectedError,
                              boolean waitFor) {
        updateRenameCapabilities(jsonPrepareRenameResponse);
        int delay = waitFor ? 100 : 750; // if not waiting for the server, make sure the server is "slow"
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(delay);
        PsiFile file = myFixture.configureByText(fileName, text);

        // Prepare rename response
        if (jsonPrepareRenameResponse != null) {
            if (PREPARE_RENAME_NO_RESULT.equals(jsonPrepareRenameResponse)) {
                // Prepare rename should return null.
                MockLanguageServer.INSTANCE.setPrepareRenameProcessor(params -> null);
            } else if (jsonPrepareRenameResponse.contains("code")) {
                // prepare rename with error
                // {
                //  "code": -32602,
                //  "message": "no identifier found"
                // }
                ResponseError error = JSONUtils.getLsp4jGson().fromJson(jsonPrepareRenameResponse, ResponseError.class);
                MockLanguageServer.INSTANCE.setPrepareRenameProcessor(params -> {
                    throw new ResponseErrorException(error);
                });
            } else {
                // Prepare rename response
                Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRenameResponse = getPrepareRenameResponse(jsonPrepareRenameResponse);
                MockLanguageServer.INSTANCE.setPrepareRenameProcessor(params -> prepareRenameResponse);
            }
        }

        // Rename response
        if (jsonRenameResponse != null) {
            if (jsonRenameResponse.contains("code")) {
                // rename with error
                // {
                //  "code": -32602,
                //  "message": "no identifier found"
                // }
                ResponseError error = JSONUtils.getLsp4jGson().fromJson(jsonRenameResponse, ResponseError.class);
                MockLanguageServer.INSTANCE.setRenameProcessor(params -> {
                    throw new ResponseErrorException(error);
                });
            } else {
                // WorkspaceEdit
                jsonRenameResponse = jsonRenameResponse.formatted(LSPIJUtils.toUri(file));
                WorkspaceEdit workspaceEdit = JSONUtils.getLsp4jGson().fromJson(jsonRenameResponse, WorkspaceEdit.class);
                MockLanguageServer.INSTANCE.setRenameProcessor(params -> workspaceEdit);
            }
        }

        if (waitFor) {
            // As rename support works only when language server is started
            // to avoid having the error "Rename... is not available during language servers starting."
            // we wait for some ms.
            try {
                LanguageServiceAccessor.getInstance(file.getProject())
                        .getLanguageServers(file, null, null)
                        .get(5000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        final AtomicReference<String> actualError = new AtomicReference<>();

        var handler = new LSPRenameUnitTestMode.LSPRenameUnitTestModeHandler() {

            @Override
            public void showErrorHint(String errorHintText) {
                if (expectedError == null) {
                    Assert.assertNull("Should not have error while renaming", errorHintText);
                }
                actualError.set(errorHintText);
            }

            @Override
            public void showRenameRefactoringDialog(RenameParams renameParams) {
                Assert.assertEquals("Placeholder should be equals", expectedPlaceholder, renameParams.getNewName());
            }
        };
        LSPRenameUnitTestMode.set(handler);

        // Do Rename...
        myFixture.performEditorAction(IdeActions.ACTION_RENAME);

        if (expectedError != null) {
            assertEquals(expectedError, actualError.get());
        } else {
            assertEquals(expectedRenamedText, myFixture.getEditor().getDocument().getText());
        }
    }

    @NotNull
    private static Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> getPrepareRenameResponse(@NotNull String jsonPrepareRenameResult) {
        Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepareRenameResponse = null;
        if (jsonPrepareRenameResult.contains("range")) {
            prepareRenameResponse = Either3.forSecond(JSONUtils.getLsp4jGson().fromJson(jsonPrepareRenameResult, PrepareRenameResult.class));
        } else if (jsonPrepareRenameResult.contains("start")) {
            prepareRenameResponse = Either3.forFirst(JSONUtils.getLsp4jGson().fromJson(jsonPrepareRenameResult, Range.class));
        } else {
            prepareRenameResponse = Either3.forThird(JSONUtils.getLsp4jGson().fromJson(jsonPrepareRenameResult, PrepareRenameDefaultBehavior.class));
        }
        return prepareRenameResponse;
    }

    private static void updateRenameCapabilities(@Nullable String jsonPrepareRenameResult) {
        Either<Boolean, RenameOptions> renameProvider;
        if (jsonPrepareRenameResult != null) {
            RenameOptions prepareRenameProvider = new RenameOptions();
            prepareRenameProvider.setPrepareProvider(true);
            renameProvider = Either.forRight(prepareRenameProvider);
        } else {
            renameProvider = Either.forLeft(Boolean.TRUE);
        }
        ServerCapabilities serverCapabilities = MockLanguageServer.defaultServerCapabilities();
        serverCapabilities.setRenameProvider(renameProvider);
        MockLanguageServer.reset(() -> serverCapabilities);
    }

}

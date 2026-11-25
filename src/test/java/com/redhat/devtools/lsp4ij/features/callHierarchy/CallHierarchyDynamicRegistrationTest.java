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
package com.redhat.devtools.lsp4ij.features.callHierarchy;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPCallHierarchyFeature;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.CallHierarchyRegistrationOptions;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A language server which announces no call hierarchy in its capabilities, and registers it dynamically under the
 * method of its request, 'textDocument/prepareCallHierarchy', gets call hierarchy requests for the documents of the
 * registration, until it unregisters it.
 */
public class CallHierarchyDynamicRegistrationTest extends LSPCodeInsightFixtureTestCase {

    private static final String REGISTRATION_ID = "call-hierarchy";

    public CallHierarchyDynamicRegistrationTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // The default capabilities of the mock language server have no call hierarchy
        MockLanguageServer.reset();
    }

    public void testCallHierarchyRegisteredDynamicallyIsSupportedUntilItIsUnregistered() throws Exception {
        assertRegistrationEnablesCallHierarchy("textDocument/prepareCallHierarchy");
    }

    public void testCallHierarchyRegisteredUnderTheFormerMethodNameIsSupported() throws Exception {
        assertRegistrationEnablesCallHierarchy("textDocument/callHierarchy");
    }

    private void assertRegistrationEnablesCallHierarchy(String method) throws Exception {
        var file = myFixture.configureByText("main.foo", "main");
        var other = myFixture.addFileToProject("other.foo", "other");
        var feature = connect(file);
        assertFalse("without a registration call hierarchy must not be supported", feature.isCallHierarchySupported(file));
        assertEquals("without a registration no call hierarchy request must be sent", 0, prepareCallHierarchy(file).size());

        var options = new CallHierarchyRegistrationOptions();
        options.setDocumentSelector(List.of(new DocumentFilter(null, null, Either.forLeft("**/main.foo"))));
        await(client().registerCapability(new RegistrationParams(List.of(new Registration(REGISTRATION_ID, method, options)))));

        PlatformTestUtil.waitWithEventsDispatching("call hierarchy registered as '" + method + "' must be supported for a document of its selector",
                () -> feature.isCallHierarchySupported(file), 10);
        assertFalse("call hierarchy must not be supported for a document outside the selector of the registration",
                feature.isCallHierarchySupported(other));
        var items = prepareCallHierarchy(file);
        assertEquals("textDocument/prepareCallHierarchy must be sent to the server", 1, items.size());
        assertEquals("a", items.get(0).callHierarchyItem().getName());

        await(client().unregisterCapability(new UnregistrationParams(List.of(new Unregistration(REGISTRATION_ID, method)))));

        PlatformTestUtil.waitWithEventsDispatching("unregistered call hierarchy must no longer be supported",
                () -> !feature.isCallHierarchySupported(file), 10);
        assertEquals("after the unregistration no call hierarchy request must be sent", 0, prepareCallHierarchy(file).size());
    }

    private LSPCallHierarchyFeature connect(PsiFile file) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS)
                .get(0)
                .getClientFeatures()
                .getCallHierarchyFeature();
    }

    /**
     * Asks for the call hierarchy at the start of the file, as the call hierarchy view does, with a support that has no
     * cached answer.
     */
    private static List<CallHierarchyItemData> prepareCallHierarchy(PsiFile file) throws Exception {
        var params = new LSPCallHierarchyPrepareParams(new TextDocumentIdentifier(), new Position(0, 0), 0);
        var items = new LSPPrepareCallHierarchySupport(file).getPrepareCallHierarchies(params);
        PlatformTestUtil.waitWithEventsDispatching("the call hierarchy must be prepared", items::isDone, 10);
        return items.get();
    }

    private static void await(CompletableFuture<?> future) throws Exception {
        PlatformTestUtil.waitWithEventsDispatching("the client must answer", future::isDone, 10);
        future.get();
    }

    private static LanguageClient client() {
        return MockLanguageServer.INSTANCE.getRemoteProxies().get(0);
    }
}

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
package com.redhat.devtools.lsp4ij.features.typeHierarchy;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.client.features.LSPTypeHierarchyFeature;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyRegistrationOptions;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A language server which announces no type hierarchy in its capabilities, and registers it dynamically under the
 * method of its request, 'textDocument/prepareTypeHierarchy', gets type hierarchy requests for the documents of the
 * registration, until it unregisters it.
 */
public class TypeHierarchyDynamicRegistrationTest extends LSPCodeInsightFixtureTestCase {

    private static final String REGISTRATION_ID = "type-hierarchy";

    public TypeHierarchyDynamicRegistrationTest() {
        super("*.foo");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockLanguageServer.reset(() -> {
            var capabilities = MockLanguageServer.defaultServerCapabilities();
            capabilities.setTypeHierarchyProvider((Either<Boolean, TypeHierarchyRegistrationOptions>) null);
            return capabilities;
        });
    }

    public void testTypeHierarchyRegisteredDynamicallyIsSupportedUntilItIsUnregistered() throws Exception {
        assertRegistrationEnablesTypeHierarchy("textDocument/prepareTypeHierarchy");
    }

    public void testTypeHierarchyRegisteredUnderTheFormerMethodNameIsSupported() throws Exception {
        assertRegistrationEnablesTypeHierarchy("textDocument/typeHierarchy");
    }

    private void assertRegistrationEnablesTypeHierarchy(String method) throws Exception {
        var file = myFixture.configureByText("main.foo", "main");
        var other = myFixture.addFileToProject("other.foo", "other");
        var feature = connect(file);
        assertFalse("without a registration type hierarchy must not be supported", feature.isTypeHierarchySupported(file));
        assertEquals("without a registration no type hierarchy request must be sent", 0, prepareTypeHierarchy(file).size());

        var options = new TypeHierarchyRegistrationOptions();
        options.setDocumentSelector(List.of(new DocumentFilter(null, null, Either.forLeft("**/main.foo"))));
        await(client().registerCapability(new RegistrationParams(List.of(new Registration(REGISTRATION_ID, method, options)))));

        PlatformTestUtil.waitWithEventsDispatching("type hierarchy registered as '" + method + "' must be supported for a document of its selector",
                () -> feature.isTypeHierarchySupported(file), 10);
        assertFalse("type hierarchy must not be supported for a document outside the selector of the registration",
                feature.isTypeHierarchySupported(other));
        var items = prepareTypeHierarchy(file);
        assertEquals("textDocument/prepareTypeHierarchy must be sent to the server", 1, items.size());
        assertEquals("a", items.get(0).typeHierarchyItem().getName());

        await(client().unregisterCapability(new UnregistrationParams(List.of(new Unregistration(REGISTRATION_ID, method)))));

        PlatformTestUtil.waitWithEventsDispatching("unregistered type hierarchy must no longer be supported",
                () -> !feature.isTypeHierarchySupported(file), 10);
        assertEquals("after the unregistration no type hierarchy request must be sent", 0, prepareTypeHierarchy(file).size());
    }

    private LSPTypeHierarchyFeature connect(PsiFile file) throws Exception {
        return LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS)
                .get(0)
                .getClientFeatures()
                .getTypeHierarchyFeature();
    }

    /**
     * Asks for the type hierarchy at the start of the file, as the type hierarchy view does, with a support that has no
     * cached answer.
     */
    private static List<TypeHierarchyItemData> prepareTypeHierarchy(PsiFile file) throws Exception {
        var params = new LSPTypeHierarchyPrepareParams(new TextDocumentIdentifier(), new Position(0, 0), 0);
        var items = new LSPPrepareTypeHierarchySupport(file).getPrepareTypeHierarchies(params);
        PlatformTestUtil.waitWithEventsDispatching("the type hierarchy must be prepared", items::isDone, 10);
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

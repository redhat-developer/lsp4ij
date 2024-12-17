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
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.AbstractLSPDocumentFeatureSupport;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * LSP usage support which collect:
 * <ul>
 *      <li>textDocument/declaration</li>
 *      <li>textDocument/definition</li>
 *      <li>textDocument/typeDefinition</li>
 *      <li>textDocument/references</li>
 *      <li>textDocument/implementation</li>
 *  </ul>
 */
public class LSPUsageSupport extends AbstractLSPDocumentFeatureSupport<LSPUsageSupport.LSPUsageSupportParams, List<LSPUsagePsiElement>> {

    public record LSPUsageSupportParams(@NotNull Position position) {}

    public LSPUsageSupport(@NotNull PsiFile file) {
        super(file, false);
    }

    @Override
    protected CompletableFuture<List<LSPUsagePsiElement>> doLoad(LSPUsageSupportParams params, CancellationSupport cancellationSupport) {
        PsiFile file = super.getFile();
        return collectUsages(file, params, cancellationSupport);
    }

    private static @NotNull CompletableFuture<List<LSPUsagePsiElement>> collectUsages(@NotNull PsiFile file,
                                                                                      @NotNull LSPUsageSupportParams params,
                                                                                      @NotNull CancellationSupport cancellationSupport) {
        var textDocumentIdentifier = LSPIJUtils.toTextDocumentIdentifier(file.getVirtualFile());
        Project project = file.getProject();
        return getLanguageServers(file,
                        f -> f.getUsageFeature().isEnabled(file),
                        f -> f.getUsageFeature().isSupported(file))
                .thenComposeAsync(languageServers -> {
                    // Here languageServers is the list of language servers which matches the given file
                    // and which have usage (references, implementation, etc) capability
                    if (languageServers.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    DeclarationParams declarationParams = new DeclarationParams(textDocumentIdentifier, params.position());
                    DefinitionParams definitionParams = new DefinitionParams(textDocumentIdentifier, params.position());
                    TypeDefinitionParams typeDefinitionParams = new TypeDefinitionParams(textDocumentIdentifier, params.position());
                    ReferenceParams referenceParams = createReferenceParams(textDocumentIdentifier, params.position(), project);
                    ImplementationParams implementationParams = new ImplementationParams(textDocumentIdentifier, params.position());

                    List<CompletableFuture<List<LSPUsagePsiElement>>> allFutures = new ArrayList<>();
                    for (var ls : languageServers) {

                        var clientFeature = ls.getClientFeatures();
                        // Collect declarations
                        if (clientFeature.getDeclarationFeature().isDeclarationSupported(file)) {
                            updateTextDocumentUri(declarationParams.getTextDocument(), file, ls);
                            allFutures.add(
                                    // Update textDocument Uri with custom file Uri if needed
                                    cancellationSupport.execute(ls
                                                    .getTextDocumentService()
                                                    .declaration(declarationParams), ls, LSPRequestConstants.TEXT_DOCUMENT_DECLARATION)
                                            .handle(reportUsages(ls, project, LSPUsagePsiElement.UsageKind.declarations))
                            );
                        }

                        // Collect definitions
                        if (clientFeature.getDefinitionFeature().isDefinitionSupported(file)) {
                            updateTextDocumentUri(definitionParams.getTextDocument(), file, ls);
                            allFutures.add(
                                    cancellationSupport.execute(ls
                                                    .getTextDocumentService()
                                                    .definition(definitionParams), ls, LSPRequestConstants.TEXT_DOCUMENT_DEFINITION)
                                            .handle(reportUsages(ls, project, LSPUsagePsiElement.UsageKind.definitions))
                            );
                        }

                        // Collect type definitions
                        if (clientFeature.getTypeDefinitionFeature().isTypeDefinitionSupported(file)) {
                            updateTextDocumentUri(typeDefinitionParams.getTextDocument(), file, ls);
                            allFutures.add(
                                    cancellationSupport.execute(ls
                                                    .getTextDocumentService()
                                                    .typeDefinition(typeDefinitionParams), ls, LSPRequestConstants.TEXT_DOCUMENT_TYPE_DEFINITION)
                                            .handle(reportUsages(ls, project, LSPUsagePsiElement.UsageKind.typeDefinitions))
                            );
                        }

                        // Collect references
                        if (clientFeature.getReferencesFeature().isReferencesSupported(file)) {
                            updateTextDocumentUri(referenceParams.getTextDocument(), file, ls);
                            allFutures.add(
                                    cancellationSupport.execute(ls
                                                    .getTextDocumentService()
                                                    .references(referenceParams), ls, LSPRequestConstants.TEXT_DOCUMENT_REFERENCES)
                                            .handle(reportUsages2(ls, project, LSPUsagePsiElement.UsageKind.references))
                            );
                        }

                        // Collect implementation
                        if (clientFeature.getImplementationFeature().isImplementationSupported(file)) {
                            updateTextDocumentUri(implementationParams.getTextDocument(), file, ls);
                            allFutures.add(
                                    cancellationSupport.execute(ls
                                                    .getTextDocumentService()
                                                    .implementation(implementationParams), ls, LSPRequestConstants.TEXT_DOCUMENT_IMPLEMENTATION)
                                            .handle(reportUsages(ls, project, LSPUsagePsiElement.UsageKind.implementations))
                            );
                        }

                    }

                    // Merge list of textDocument/references future in one future which return the list of location information
                    return CompletableFutures.mergeInOneFuture(allFutures, cancellationSupport);
                });
    }

    private static BiFunction<? super List<? extends Location>, Throwable, ? extends List<LSPUsagePsiElement>> reportUsages2(
            LanguageServerItem ls, Project project,
            LSPUsagePsiElement.UsageKind usageKind) {
        return (locations, error) -> {
            if (error != null) {
                return Collections.emptyList();
            }
            return createUsages(locations, ls.getClientFeatures(), usageKind, project);
        };
    }

    @NotNull
    private static BiFunction<Either<List<? extends Location>, List<? extends LocationLink>>, Throwable, List<LSPUsagePsiElement>> reportUsages(
            LanguageServerItem ls, @NotNull Project project,
            @NotNull LSPUsagePsiElement.UsageKind usageKind) {
        return (locations, error) -> {
            if (error != null) {
                // How to report error ?
                // - in the log it is a bad idea since it is an error in language server and some ls like go throw an error when there are no response
                // - in the Find usages tree, it should be a good idea, bit how to manage that?
                return Collections.emptyList();
            }
            return createUsages(locations, ls.getClientFeatures(), usageKind, project);
        };
    }

    private static List<LSPUsagePsiElement> createUsages(@Nullable List<? extends Location> locations,
                                                         @Nullable FileUriSupport fileUriSupport,
                                                         @NotNull LSPUsagePsiElement.UsageKind usageKind,
                                                         @NotNull Project project) {
        if (locations == null || locations.isEmpty()) {
            return Collections.emptyList();
        }
        return locations
                .stream()
                .map(location -> LSPUsagesManager.toPsiElement(location, fileUriSupport, usageKind, project))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<LSPUsagePsiElement> createUsages(@Nullable Either<List<? extends Location>, List<? extends LocationLink>> locations,
                                                         @Nullable FileUriSupport fileUriSupport,
                                                         @Nullable LSPUsagePsiElement.UsageKind usageKind,
                                                         @Nullable Project project) {
        if (locations == null) {
            return Collections.emptyList();
        }
        if (locations.isLeft()) {
            return createUsages(locations.getLeft(), fileUriSupport, usageKind, project);
        }
        return createUsagesFromLocationLinks(locations.getRight(), fileUriSupport, usageKind, project);
    }

    private static List<LSPUsagePsiElement> createUsagesFromLocationLinks(@Nullable List<? extends LocationLink> locations,
                                                                          @Nullable FileUriSupport fileUriSupport,
                                                                          @NotNull LSPUsagePsiElement.UsageKind usageKind,
                                                                          @NotNull Project project) {
        if (locations == null || locations.isEmpty()) {
            return Collections.emptyList();
        }
        return locations
                .stream()
                .map(location -> LSPUsagesManager.toPsiElement(location, fileUriSupport, usageKind, project))
                .filter(Objects::nonNull)
                .toList();
    }

    private static ReferenceParams createReferenceParams(@NotNull TextDocumentIdentifier textDocument, @NotNull Position position, @NotNull Project project) {
        ReferenceContext context = new ReferenceContext();
        // TODO: manage "IncludeDeclaration" with a settings
        context.setIncludeDeclaration(true);
        return new ReferenceParams(textDocument, position, context);
    }



}

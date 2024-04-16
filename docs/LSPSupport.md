# LSP support

The current implementation of `LSP4IJ` does not yet fully adhere to the LSP (Language Server Protocol) specification. This section provides an overview of the supported LSP features for IntelliJ:

## Base support

Current state of [Base protocol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#baseProtocol) support:

* ✅ [$/cancelRequest](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest).
* ✅ [$/progress](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#progress) (see [implementation details](#progress-support))

## Text Document Synchronization

Current state of [Text Document Synchronization](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_synchronization) support:

 * ✅ [textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didOpen).
 * ✅ [textDocument/didChange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didChange).
 * ✅ [textDocument/didClose](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didClose).
 * ✅ [textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didSave).
 * ❌ [textDocument/willSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSave).
 * ❌ [textDocument/willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSaveWaitUntil).
 
Notebooks are not supported.
 
## Language Features

Current state of [Language Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#languageFeatures) support:

 * ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) (see [implementation details](#go-to-definition))
 * ✅ [textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) (see [implementation details](#document-highlight))
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) (see [implementation details](#document-link))
 * ❌ [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#documentLink_resolve).
 * ✅ [textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) (see [implementation details](#hover))
 * ✅ [textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) (see [implementation details](#codeLens))
 * ✅ [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_resolve)
 * ❌ [workspace/codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_refresh)
 * ✅ [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) (see [implementation details](#inlayhint))
 * ❌ [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve)
 * ✅ [workspace/inlayHint/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlayHint_refresh) 
 * ✅ [textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) (see [implementation details](#completion-proposals))
 * ✅ [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) (see [implementation details](#completion-item-resolve))
 * ✅ [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp) (see [implementation details](#signature-help))
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction) (see [implementation details](#codeAction))
 * ✅ [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeAction_resolve)
 * ✅ [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) (see [implementation details](#documentColor))
 * ❌ [textDocument/colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_colorPresentation).
 * ✅ [textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration) (see [implementation details](#declaration))
 * ✅ [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition) (see [implementation details](#type-definition))
 * ✅ [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation) (see [implementation details](#implementation))
 * ✅ [textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) (see [implementation details](#references))
 * ❌ [textDocument/prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareCallHierarchy).
 * ❌ [textDocument/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_incomingCalls).
 * ❌ [textDocument/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_outgoingCalls).
 * ❌ [textDocument/prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareTypeHierarchy).
 * ❌ [typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_supertypes).
 * ✅ [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange)  (see [implementation details](#folding-range))
 * ❌ [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_selectionRange).
 * ❌ [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol).
 * ❌ [textDocument/semanticTokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens).
 * ❌ [textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue).
 * ❌ [workspace/inlineValue/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlineValue_refresh).
 * ❌ [textDocument/moniker](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_moniker).
 * ❌ [textDocument/pullDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_pullDiagnostics).
 * ✅ [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting) (see [implementation details](#formatting))
 * ✅ [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting) (see [implementation details](#formatting)).
 * ❌ [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_onTypeFormatting).
 * ✅ [textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareRename).
 * ✅ [textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rename) (see [implementation details](#rename)).
 * ❌ [textDocument/linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_linkedEditingRange).

## Workspace Features

Current state of [Workspace Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceFeatures) support:

 * ✅ [workspace/didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWatchedFiles).
 * ✅ [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_executeCommand).
 * ✅ [workspace/applyEdit](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_applyEdit).
 * ❌ [workspace/symbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_symbol).
 * ❌ [workspace/symbolResolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_symbolResolve).
 * ❌ [workspace/configuration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_configuration).
 * ❌ [workspace/workspaceFolders](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_workspaceFolders).
 * ❌ [workspace/didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWorkspaceFolders).
 * ❌ [workspace/willCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willCreateFiles).
 * ❌ [workspace/didCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didCreateFiles).
 * ✅ [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles).
 * ✅ [workspace/didRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didRenameFiles).
 * ❌ [workspace/willDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles).
 * ❌ [workspace/didDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didDeleteFiles).
 
## Window Features

Current state of [Window Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#windowFeatures) support

 * ✅ [window/showMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage). (see [implementation details](#show-message))
 * ✅ [window/showMessageRequest](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest) (see [implementation details](#show-message-request))
 * ✅ [window/logMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_logMessage) (see [implementation details](./UserGuide.md#lsp-console))
 * ✅ [window/showDocument](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showDocument).
 * ❌ [telemetry/event](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#telemetry_event).

## Implementation details

#### Progress support

[$/progress](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#progress) is implemented with `Background Tasks`.

Here a sample with [Eclipse JDT Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls):

![$/progress](./images/lsp-support/progress.png)

#### Go to Definition

[textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) is implemented via the  
`gotoDeclarationHandler` extension point. As this extension point supports `any` language, it works out-of-the-box.

It is also called via [Find Usages](./UserGuide.md#find-usages) to show definitions.

#### References

The [textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) is consumed with:

* the `Navigate / LSP Reference(s)` global menu.
* or with the `Go To/ LSP Reference(s)` editor menu.

![textDocument/references menu](./images/lsp-support/textDocument_references_menu.png)

This menu action either opens the reference in a popup or navigates to the reference if there are several references:

![textDocument/implementation popup](./images/lsp-support/textDocument_references_popup.png)

[textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) is used too via [Find Usages](./UserGuide.md#find-usages) to show references.

#### Implementation

The [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation) is consumed with:

* the `Navigate / LSP Implementation(s)` global menu.
* or with the `Go To/ LSP Implementation(s)` editor menu.

![textDocument/implementation menu](./images/lsp-support/textDocument_implementation_menu.png)

This menu action either opens the implementation in a popup or navigates to the implementation if there are several implementations:

![textDocument/implementation popup](./images/lsp-support/textDocument_implementation_popup.png)

[textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation) is too used via [Find Usages](./UserGuide.md#find-usages) to show implementations.

#### Type definition

The [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition) is consumed with:

 * the `Navigate / LSP Type Definition(s)` global menu.
 * or with the `Go To/ Type Definition(s)` editor menu.

This menu action either opens the type definition in a popup or navigates to the type definition if there are several type definitions:

[textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition) is used too via [Find Usages](./UserGuide.md#find-usages) to show Type definitions.

#### Declaration

The [textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration) is consumed with:

 * the `Navigate / LSP Declaration(s)` global menu.
 * or with the `Go To/ Declaration(s)` editor menu.

This menu action either opens the declaration in a popup or navigates to the declaration if there are several declarations:

[textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration) is used too via [Find Usages](./UserGuide.md#find-usages) to show declarations.

#### Document Highlight

[textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) is implemented via the 
`highlightUsagesHandlerFactory` extension point. As this extension point supports `any` language, it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls), highlighting item variables:

![textDocument/documentHighlight](./images/lsp-support/textDocument_documentHighlight.png)

#### Document Link

[textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) is implemented via:

* an `externalAnnotator` extension point, to display a `documentLink` with an hyperlink renderer.
* a `gotoDeclarationHandler` extension point, to open the file of the `documentLink`.`

As those extension points support `any` language, `textDocument/documentLink` works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls), displaying an `include` template with an hyperlink renderer (Ctrl+Click opens the document link):

![textDocument/documentLink](./images/lsp-support/textDocument_documentLink.png)

#### Hover

[textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) is implemented with `platform.backend.documentation.targetProvider` 
extension point to support any language and `textDocument/hover` works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing documentation while hovering over an `include` section:

![textDocument/hover](./images/lsp-support/textDocument_hover.png)

#### CodeLens

[textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) is implemented with the `codeInsight.codeVisionProvider` extension point.
As LSP4IJ registers [LSPCodeLensProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/codeLens/LSPCodeLensProvider.java) 
for all languages associated with a language server, it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls), which shows REST services URL with codeLens:

![textDocument/codeLens](./images/lsp-support/textDocument_codeLens.png)

#### InlayHint

[textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) is implemented with the `codeInsight.inlayProvider` extension point.
LSP4IJ registers [LSPInlayHintProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/inlayhint/LSPInlayHintsProvider.java) for all languages associated with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/LSPInlayHintProvidersFactory.java), so it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing the parameter's Java type as inlay hint:

![textDocument/inlayHint](./images/lsp-support/textDocument_inlayHint.png)

#### DocumentColor

[textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) is implemented with the `codeInsight.inlayProvider` extension point.
LSP4IJ registers [LSPColorProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/color/LSPColorProvider.java) for all languages associated with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/LSPInlayHintProvidersFactory.java), so it works out-of-the-box.

Here is an example with the [CSS language server](https://github.com/microsoft/vscode-css-languageservice) showing the color's declaration with a colored square:

![textDocument/documentColor](./images/lsp-support/textDocument_documentColor.png)

#### Completion Proposals

[textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) is implemented with 
[LSPCompletionContributor](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/completion/LSPCompletionContributor.java) class
declared with the
`completion.contributor` extension point. As this extension point supports `any` language, it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing method completion:

![textDocument/completion](./images/lsp-support/textDocument_completion.png)

##### Completion item resolve

The [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) request is implemented to resolve:

 * the `documentation` property of a completionItem.

Here a sample with [TypeScript Language Server](./user-defined-ls/typescript-language-server.md) completion item which resolves and shows `documentation` when the completion item is selected:

![completionItem/resolve/documentation](./images/lsp-support/completionItem_resolve_documentation.png)
 
 * the `detail` property of a completionItem.

Here a sample with [TypeScript Language Server](./user-defined-ls/typescript-language-server.md) completion item which resolves and shows `detail` when the completion item is selected: 

![completionItem/resolve/detail](./images/lsp-support/completionItem_resolve_detail.png)

 * the `additionalTextEdits` property of a completionItem.

#### Signature Help

[textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp) is implemented with
the `codeInsight.parameterInfo` extension point. By default, LSP4IJ registers the `codeInsight.parameterInfo` with 
[LSPParameterInfoHandler](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/signatureHelp/LSPParameterInfoHandler.java) class for `TEXT` and `textmate` languages:

```xml
<!-- LSP textDocument/signatureHelp -->
<codeInsight.parameterInfo
        id="LSPParameterInfoHandlerForTEXT"
        language="TEXT"
        implementationClass="com.redhat.devtools.lsp4ij.features.signatureHelp.LSPParameterInfoHandler"/>

<codeInsight.parameterInfo
id="LSPParameterInfoHandlerForTextMate"
language="textmate"
implementationClass="com.redhat.devtools.lsp4ij.features.signatureHelp.LSPParameterInfoHandler"/>
```

If you use another language, you will have to declare `codeInsight.parameterInfo` with your language.

Here is an example with the [TypeScript Language Server](./user-defined-ls/typescript-language-server.md) showing signature help:

![textDocument/signatureHelp](./images/lsp-support/textDocument_signatureHelp.gif)

#### Folding range

[textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange) is implemented with
the `lang.foldingBuilder` extension point. By default, LSP4IJ registers the `lang.foldingBuilder` with
[LSPFoldingRangeBuilder](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/foldingRange/LSPFoldingRangeBuilder.java) class for `TEXT` and `textmate` languages:

```xml
<!-- LSP textDocument/folding -->
<lang.foldingBuilder id="LSPFoldingBuilderForText"
                     language="TEXT"
                     implementationClass="com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder"
                     order="first"/>

<lang.foldingBuilder id="LSPFoldingBuilderForTextMate"
                     language="textmate"
                     implementationClass="com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder"
                     order="first"/>
```

If you use another language, you will have to declare `lang.foldingBuilder` with your language.

Here is an example with the [Go Language Server](./user-defined-ls/gopls.md) showing folding:

![textDocument/foldingRange](./images/lsp-support/textDocument_foldingRange.gif)

#### Publish Diagnostics

[textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) is implemented with an `externalAnnotator` extension point. As this extension point supports `any` language, it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) reporting errors:

![textDocument/publishDiagnostics](./images/lsp-support/textDocument_publishDiagnostics.png)

#### Code Action

Here is an example featuring the [Clojure LSP](./user-defined-ls/clojure-lsp.md), which offers code actions:

 * Quickfix code action (at the top): This type of code action is facilitated by registering a `quick fix within the IntelliJ annotation`.
 * Other code actions such as refactoring (at the bottom): These kinds of code actions are handled using `Intentions`.

![textDocument/codeAction](./images/lsp-support/textDocument_codeAction.png)

For the last type of code action, you can enable/disable them using the `Intentions / Language Server` preference setting.

![Language Server Intentions](./images/lsp-support/textDocument_codeAction_intentions.png)

#### Rename

[textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rename) is implemented with [LSPRenameHandler](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/rename/LSPRenameHandler.java) class 
declared with the `renameHandler` extension point.

Here is an example with the [TypeScript Language Server](./user-defined-ls/typescript-language-server.md) showing rename of function name:

![textDocument/rename](./images/lsp-support/textDocument_rename.gif)

#### Formatting

[textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) / [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting) are implemented with 
[LSPFormattingOnlyService](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/formatting/LSPFormattingOnlyService.java) /
[LSPFormattingAndRangeBothService](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/formatting/LSPFormattingAndRangeBothService.java) with the `formattingService` extension point.

#### Show Message

[window/showMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage) supports Markdown messages and clickable links.

Here is an example with [Rust Analyzer](https://rust-analyzer.github.io/) reporting an error:

```markdown
{
  "type": 1,
  "message": "Failed to discover workspace.\nConsider adding the `Cargo.toml` of the workspace to the [`linkedProjects`](https://rust-analyzer.github.io/manual.html#rust-analyzer.linkedProjects) setting.\n\nFailed to load workspaces."
}
```
is rendered as a `Balloon` notification: 

![window/showMessage](./images/lsp-support/window_showMessage.png)

You can change the notification behavior of `LSP/window/showMessage` by using the standard UI `Notifications` preferences :

![window/showMessage Notification](./images/lsp-support/window_showMessage_Notification.png)

## Show Message Request

[window/showMessageRequest](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest) is supported.

Here is an example with [Scala Language Server (MetaLS)](./user-defined-ls/metals.md) reporting a message request:

```json
{
  "actions": [
    {
      "title": "Create .scalafmt.conf"
    },
    {
      "title": "Run anyway"
    },
    {
      "title": "Not now"
    }
  ],
  "type": 1,
  "message": "No .scalafmt.conf file detected. How would you like to proceed:"
}
```
is rendered as a `Sticky balloon` notification:

![window/showMessageRequest](./images/lsp-support/window_showMessageRequest.png)

You can change the notification behavior of `LSP/window/showMessageRequest` by using the standard UI `Notifications` preferences :

![window/showMessageRequest Notification](./images/lsp-support/window_showMessageRequest_Notification.png)
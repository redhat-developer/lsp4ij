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
 * ❌ [textDocument/didRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didRename).
 
Notebooks are not supported.
 
## Language Features

Current state of [Language Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#languageFeatures) support:

 * ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) (see [implementation details](#go-to-definition))
 * ✅ [textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) (see [implementation details](#document-highlight))
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) (see [implementation details](#document-link))
 * ✅ [textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) (see [implementation details](#hover))
 * ✅ [textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) (see [implementation details](#codelens))
 * ✅ [codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_resolve)
 * ❌ [workspace/codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_refresh)
 * ✅ [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) (see [implementation details](#inlayhint))
 * ❌ [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve)
 * ✅ [workspace/inlayHint/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlayHint_refresh) 
 * ✅ [textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) (see [implementation details](#completion-proposals))
 * ✅ [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) 
 * ✅ [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp) (see [implementation details](#signature-help))
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition)
 * ✅ [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction)
 * ✅ [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeAction_resolve)
 * ✅ [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) (see [implementation details](#documentColor))
 * ❌ [textDocument/colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_colorPresentation).
 * ✅ [textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration).
 * ✅ [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition).
 * ✅ [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation).
 * ✅ [textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) (see [implementation details](#references))
 * ❌ [textDocument/prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareCallHierarchy).
 * ❌ [textDocument/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_incomingCalls).
 * ❌ [textDocument/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_outgoingCalls).
 * ❌ [textDocument/prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareTypeHierarchy).
 * ❌ [typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_supertypes).
 * ❌ [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#documentLink_resolve).
 * ❌ [codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_refresh).
 * ✅ [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange)  (see [implementation details](#folding-range))
 * ❌ [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_selectionRange).
 * ❌ [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol).
 * ❌ [textDocument/semanticTokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens).
 * ❌ [textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue).
 * ❌ [workspace/inlineValue/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlineValue_refresh).
 * ❌ [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve).
 * ❌ [textDocument/moniker](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_moniker).
 * ❌ [textDocument/pullDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_pullDiagnostics).
 * ✅ [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting) (see [implementation details](#formatting))
 * ✅ [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting) (see [implementation details](#formatting)).
 * ❌ [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_onTypeFormatting).
 * ❌ [textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rename).
 * ❌ [textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareRename).
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
 * ❌ [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles).
 * ❌ [workspace/didRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didRenameFiles).
 * ❌ [workspace/willDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles).
 * ❌ [workspace/didDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didDeleteFiles).
 
## Window Features

Current state of [Window Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#windowFeatures) support

 * ✅ [window/showMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage). (see [implementation details](#show-message))
 * ✅ [window/showMessageRequest](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest).
 * ❌ [window/logMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_logMessage).
 * ❌ [window/showDocument](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showDocument).
 * ❌ [telemetry/event](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#telemetry_event).

## Implementation details

#### Progress support

[$/progress](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#progress) is implemented with `Background Tasks`.

Here a sample with [Rust Analyzer](https://rust-analyzer.github.io/):

![$/progress](./images/lsp-support/progress.png)

#### Go to Definition

[textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) is implemented via the  
`gotoDeclarationHandler` extension point. As this extension point supports `any` language, it works out-of-the-box.

It is also called via [Find Usages](./UserGuide.md#find-usages) to show definitions.

#### Type definition

[textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition) is used via [Find Usages](./UserGuide.md#find-usages) to show Type definitions.

#### Declaration

[textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration) is used via [Find Usages](./UserGuide.md#find-usages) to show declarations.

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

[textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) is implemented with `documentationProvider` extension point to support any language.

However, as IJ's `documentationProvider` API doesn't support aggregation of several `documentationProvider`, it takes
the first `documentationProvider` it finds.

If your language already supports `lang.documentationProvider` (`documentationProvider` for a given language),
this `lang.documentationProvider` will be used instead of the LSP `documentationProvider`.
This problem occurs for instance with `Properties` and `JAVA` languages which have their own `documentationProvider`.

To fix this issue for the `JAVA` language, you need to declare in your `plugin.xml`:

```xml 

<lang.documentationProvider
        language="JAVA"
        implementationClass="com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationProvider"
        order="first"/>
```

By default, LSP4IJ can support LSP hover when the file is associated with a `TextMate` grammar, but if your file
is associated with the `TEXT` file type (ex : once you associate your file with a user-defined file type like `CSS` files)
you will lose the LSP hover support. See some explanation [here](https://github.com/redhat-developer/lsp4ij/issues/97#issuecomment-1909804353).

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing documentation while hovering over an `include` section:

![textDocument/hover](./images/lsp-support/textDocument_hover.png)

#### CodeLens

[textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) is implemented with the `codeInsight.codeVisionProvider` extension point.
As LSP4IJ registers [LSPCodeLensProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/codelens/LSPCodeLensProvider.java) 
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
LSP4IJ registers [LSPInlayHintProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/inlayhint/LSPInlayHintsProvider.java) for all languages associated with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/features/LSPInlayHintProvidersFactory.java), so it works out-of-the-box.

Here is an example with the [CSS language server](https://github.com/microsoft/vscode-css-languageservice) showing the color's declaration with a colored square:

![textDocument/documentColor](./images/lsp-support/textDocument_documentColor.png)

#### Completion Proposals

[textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) is implemented with the
`completion.contributor` extension point. As this extension point supports `any` language, it works out-of-the-box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing method completion:

![textDocument/completion](./images/lsp-support/textDocument_completion.png)

The [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) request is implemented to resolve:

* the `documentation` property of a completionItem

It doesn't resolve:

 * `detail`
 * `additionalTextEdits`

#### Signature Help

[textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp) is implemented with
the `codeInsight.parameterInfo` extension point. By default, LSP4IJ registers the `codeInsight.parameterInfo` with 
`com.redhat.devtools.lsp4ij.features.signatureHelp.LSPParameterInfoHandler` class for `TEXT` and `textmate` languages:

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
`com.redhat.devtools.lsp4ij.features.foldingRange.LSPFoldingRangeBuilder` class for `TEXT` and `textmate` languages:

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

#### References

[textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) is used via [Find Usages](./UserGuide.md#find-usages) to show references.   

#### Implementation

[textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation) is used via [Find Usages](./UserGuide.md#find-usages) to show implementations.

#### Formatting

[textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) and [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting) are implemented with the `formattingService` extension point.

#### Show Message

[window/showMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage) supports Markdown messages and clickable links.

Here is an example with [Rust Analyzer](https://rust-analyzer.github.io/) reporting an error:

```markdown
{
  "type": 1,
  "message": "Failed to discover workspace.\nConsider adding the `Cargo.toml` of the workspace to the [`linkedProjects`](https://rust-analyzer.github.io/manual.html#rust-analyzer.linkedProjects) setting.\n\nFailed to load workspaces."
}
```
is rendered as: 

![window/showMessage](./images/lsp-support/window_showMessage.png)

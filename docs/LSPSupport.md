# LSP support

The current implementation of `LSP4IJ` does not yet fully adhere to the LSP (Language Server Protocol) specification. This section provides an overview of the supported LSP features for IntelliJ:

## Text Document Synchronization

Current state of [Text Document Synchronization](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_synchronization) support:

 * ✅ [textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didOpen).
 * ✅ [textDocument/didChange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didChange).
 * ✅ [textDocument/didClose](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didClose).
 * ✅ [textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didSave).
 * ❌ [textDocument/willSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSave).
 * ❌ [textDocument/willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSaveWaitUntil).
 * ❌ [textDocument/didRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didRename).
 * ❌ [textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didSave).
 
Notebooks are not supported.
 
## Language Features

Current state of [Language Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#languageFeatures) support:

 * ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) (see [implementation details](#go-to-definition))
 * ✅ [textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) (see [implementation details](#document-highlight))
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) (see [implementation details](#document-link))
 * ✅ [textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) (see [implementation details](#hover))
 * ✅ [textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) (see [implementation details](#codelens))
 * ✅ [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) (see [implementation details](#inlayhint))
 * ✅ [textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) (see [implementation details](#completion-proposals))
 * ✅ [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) 
 * ✅ [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) (see [implementation details](#publish-diagnostics))
 * ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition)
 * ✅ [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction)
 * ✅ [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeAction_resolve)
 * ✅ [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) (see [implementation details](#documentColor))
 * ❌ [textDocument/colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_colorPresentation).
 * ❌ [textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration).
 * ❌ [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition).
 * ❌ [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation).
 * ❌ [textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references).
 * ❌ [textDocument/prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareCallHierarchy).
 * ❌ [textDocument/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_incomingCalls).
 * ❌ [textDocument/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_outgoingCalls).
 * ❌ [textDocument/prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareTypeHierarchy).
 * ❌ [typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_supertypes).
 * ❌ [documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#documentLink_resolve).
 * ❌ [codeLens/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_refresh).
 * ❌ [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange).
 * ❌ [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_selectionRange).
 * ❌ [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol).
 * ❌ [textDocument/semanticTokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens).
 * ❌ [textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue).
 * ❌ [workspace/inlineValue/refresh](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_inlineValue_refresh).
 * ❌ [inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve).
 * ❌ [textDocument/moniker](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_moniker).
 * ❌ [textDocument/pullDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_pullDiagnostics). 
 * ❌ [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp).
 * ❌ [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting).
 * ❌ [textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting).
 * ❌ [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_onTypeFormatting).
 * ❌ [textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rename).
 * ❌ [textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareRename).
 * ❌ [textDocument/linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_linkedEditingRange).

## Workspace Features

Current state of [Workspace Features]( https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceFeatures) support:

 * ✅ [workspace/didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWatchedFiles) but partially, pattern must be supported.
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

 * ❌ [window/showMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessage).
 * ❌ [window/showMessageRequest](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showMessageRequest).
 * ❌ [window/logMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_logMessage).
 * ❌ [window/showDocument](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showDocument).
 * ❌ [window/workDoneProgress/create](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_workDoneProgress_create).
 * ❌ [window/workDoneProgress/cancel](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_workDoneProgress_cancel).
 * ❌ [telemetry/event](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#telemetry_event).

## Implementation details
#### Go to Definition

[textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) is implemented via a  
`gotoDeclarationHandler` extension point. As this extension point supports `any` language, it works out of the box.

#### Document Highlight

[textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) is implemented via  
`highlightUsagesHandlerFactory` extension point. As this extension point supports `any` language, it works out of the box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls), highlighting item variables:

![textDocument/documentHighlight](./images/lsp-support/textDocument_documentHighlight.png)

#### Document Link

[textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) is implemented via:

* an `externalAnnotator` extension point, to display `documentLink` with an hyperlink renderer.
* a `gotoDeclarationHandler` extension point, to open the file of the `documentLink`.`

As those extension points support `any` language, `textDocument/documentLink` works out of the box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls), displaying an `include` template with an hyperlink renderer (Ctrl+Click opens the document link):

![textDocument/documentLink](./images/lsp-support/textDocument_documentLink.png)

#### Hover

[textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) is implemented with `documentationProvider` extension point to support any language.
However as IJ `documentationProvider` doesn't support aggregation of several documentationProvider, it takes
the first `documentationProvider` which found.

If your language already supports `lang.documentationProvider` (`documentationProvider` for a given language),
this `lang.documentationProvider` will be used instead of the LSP `documentationProvider`.
This problem occurs for instance with `Properties` and `JAVA` languages which have their own `documentationProvider`.

To fix this issue for the `JAVA` language, you need to declare in your `plugin.xml`:

```xml 
<lang.documentationProvider 
  language="JAVA"
  implementationClass="com.redhat.devtools.lsp4ij.operations.documentation.LSPDocumentationProvider" 
  order="first"/>
```

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing documentation while hovering over an `include` section:

![textDocument/hover](./images/lsp-support/textDocument_hover.png)

#### CodeLens

[textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) is implemented with `codeInsight.inlayProvider` extension point.
As LSP4IJ register [LSPCodeLensProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/codelens/LSPCodelensProvider.java) for all languages which are associate with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/LSPInlayHintProvidersFactory.java), it works out of the box.

Here a sample with [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) which shows REST services URL with codeLens:

![textDocument/codeLens](./images/lsp-support/textDocument_codeLens.png)

#### InlayHint

[textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) is implemented with `codeInsight.inlayProvider` extension point.
As LSP4IJ register [LSPInlayHintProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/inlayhint/LSPInlayHintsProvider.java) for all languages which are associate with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/LSPInlayHintProvidersFactory.java), it works out of the box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing the parameter's Java type as inlay hint:

![textDocument/inlayHint](./images/lsp-support/textDocument_inlayHint.png)

#### DocumentColor

[textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) is implemented with `codeInsight.inlayProvider` extension point.
As LSP4IJ register [LSPInlayHintProvider](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/inlayhint/LSPInlayHintsProvider.java) for all languages which are associate with a language server with
[LSPInlayHintProvidersFactory](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/operations/LSPInlayHintProvidersFactory.java), it works out of the box.

Here is an example with the [CSS language server](https://github.com/microsoft/vscode-css-languageservice) showing the color's declaration with colorized square:

![textDocument/documentColor](./images/lsp-support/textDocument_documentColor.png)

#### Completion Proposals

[textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) is implemented with
`completion.contributor` extension point. As this extension point supports `any` language, it works out of the box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) showing method completion:

![textDocument/completion](./images/lsp-support/textDocument_completion.png)

The [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) is implemented too.

#### Publish Diagnostics

[textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics) is implemented with an `externalAnnotator` extension point. As this extension point supports `any` language, it works out of the box.

Here is an example with the [Qute language server](https://github.com/redhat-developer/quarkus-ls/tree/master/qute.ls) reporting errors:

![textDocument/publishDiagnostics](./images/lsp-support/textDocument_publishDiagnostics.png)
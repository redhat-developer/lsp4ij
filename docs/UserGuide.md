# User guide

## LSP console

Although not directly useful to users in most cases, the Language Server console view is extremely valuable 
when we need to troubleshoot issues with the language servers.

The state of the servers is visible, stop and restart is available with a right-click, and you can enable different levels of tracing:

![LSP console trace settings](./images/LSPConsoleSettings.png)

The communication details between the IDE and the language servers are seen in the "LSP consoles" pane. 
In verbose mode, the messages can be expanded for more details:

![LSP console Traces](./images/LSPConsole.png)

If the language server logs messages via [window/logMessage](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_logMessage), you can see them in the `Logs` tab:

![LSP console Logs](./images/LSPConsole_Logs.png) 

When a language server is started, several actions are available, like stopping the language server or copying the command starting the language server :

![LSP console actions](./images/LSPConsoleActions.png)

## Settings

### Language Servers preferences

The preference page under `Preferences | Languages & Frameworks | Language Servers` allows power users 
to configure mappings, language servers debugging and tracing:

![Language Server preferences](./images/LanguageServerPreferences.png)

The `Mappings` tab shows the file associations with the language server:

![Language Server preferences - Mappings](./images/LanguageServerPreferencesMappings.png)

The `Debug` tab allows to configure language servers debugging and tracing:

![Language Server preferences - Debug](./images/LanguageServerPreferencesDebug.png)

### LSP CodeLens

The standard preference page under `Preferences | Inlay Hints` allows to configure `LSP Codelens`:

![LSP CodeLens settings](./images/LSPCodeLensSettings.png)

You can configure LSP CodeLens on the `Top`:

![LSP CodeLens settings - Top](./images/LSPCodeLensSettingsTop.png)

or configure LSP CodeLens on the `Right`:

![LSP CodeLens settings - Right](./images/LSPCodeLensSettingsRight.png)

## Actions

### Formatting

The LSP formatting support can be done with the standard `Reformat Code` and `Reformat File` actions.

### Find Usages

The following LSP features are integrated with the standard `Find Usages` menu (provided they're supported by the underlying language server): 

* [Declarations](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration)
* [Definitions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition)
* [Type Definitions](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition)
* [References](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references)
* [Implementations](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation)

![Find Usages menu](./images/find-usages/FindUsagesMenu.png)

![Find Usages result](./images/find-usages/FindUsagesResult.png)

### Refactoring

#### On file operation

If the language server can support [workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles), when a file is
renamed inside IntelliJ IDEA, LSP4IJ can consume it and applies the `WorkspaceEdit`
to apply some refactoring.

## Semantic Tokens support

LSP4IJ provides **experimental**  support for [LSP Semantic Tokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens).

### Language Server coloration

LSP4IJ provides default colors for semantic tokens, configurable in `Editor / Color Scheme / Language Server`:

![Semantic tokens color settings](./images/LSPSemanticTokensColorPage.png)

You can see the mapping between token types/modifiers and IntelliJ's TextAttributeKey on the color settings page (e.g., Class declaration)
in [DefaultSemanticTokensColorsProvider](./LSPSupport.md#DefaultSemanticTokensColorsProvider) section.

### Semantic Tokens Inspector

The `Semantic Tokens Inspector` allows you to understand semantic coloring via LSP semantic tokens support. You can open the inspector
with the menu `View / Tool Window / Semantic Tokens Inspector`:

![LSP Semantic Tokens Inspector Menu](./images/LSPSemanticTokensInspectorMenu.png)

Once the inspector is open, it tracks all calls to semantic tokens. 
If a file must be colored via semantic tokens, it appears in a tab of the inspector 
and must display information on the TextAttributeKeys to use and the token types / modifiers decoded
that you can select in the left panel via the checkboxes:

![LSP Semantic Tokens Inspector](./images/LSPSemanticTokensInspector.png)

## Inspections

You can click on `Inspect Code...` hyperlink available on the `Project Errors` from the `Problems` view to 
show all LSP diagnostics errors of your project in the `Language Servers` local inspection tool:

 - from opened files.
 - from closed files, if your language server support it. 

![Inspect Code...](./images/LSPInspectCode.png)

## Troubleshooting

If the `Semantic Tokens` doesn't show the expected result, please check that:

 * show in the [LSP console](#lsp-console) that your language server reports 
LSP request `textDocument/semanticTokens/full`
 * uses the [Semantic Tokens Inspector](#semantic-tokens-inspector) to understand more how the 
file content is tokenized. Problem could come if LSP4IJ doesn't support some token types / modifiers.
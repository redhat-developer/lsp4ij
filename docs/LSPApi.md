## LSP Client Features

The [LSPClientFeatures](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/features/LSPClientFeatures.java) API allows you to customize the behavior of LSP features, including:

- [LSP codeAction feature](#lsp-codeAction-feature)
- [LSP codeLens feature](#lsp-codeLens-feature)
- [LSP color feature](#lsp-color-feature)
- [LSP completion feature](#lsp-completion-feature)
- [LSP declaration feature](#lsp-declaration-feature)
- [LSP definition feature](#lsp-definition-feature)
- [LSP diagnostic feature](#lsp-diagnostic-feature)
- [LSP documentHighlight feature](#lsp-documentHighlight-feature)
- [LSP documentLink feature](#lsp-documentLink-feature)
- [LSP documentSymbol feature](#lsp-documentSymbol-feature)
- [LSP foldingRange feature](#lsp-foldingRange-feature)
- [LSP formatting feature](#lsp-formatting-feature)
- [LSP selection feature](#lsp-selection-feature)
- [LSP hover feature](#lsp-hover-feature)
- [LSP implementation feature](#lsp-implementation-feature)
- [LSP inlayHint feature](#lsp-inlayHint-feature)
- [LSP references feature](#lsp-references-feature)
- [LSP rename feature](#lsp-rename-feature)
- [LSP semanticTokens feature](#lsp-semanticTokens-feature)
- [LSP signatureHelp feature](#lsp-signatureHelp-feature)
- [LSP typeDefinition feature](#lsp-typeDefinition-feature)
- [LSP usage feature](#lsp-usage-feature)
- [LSP workspace symbol feature](#lsp-workspace-symbol-feature)

You can extend these default features by:

- Creating custom classes that extend the `LSP*Feature` classes (e.g., creating a class `MyLSPFormattingFeature` that extends the [LSPFormattingFeature](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/features/LSPFormattingFeature.java) to customize formatting support) and overriding specific methods to modify behavior.
- Registering your custom classes using `LanguageServerFactory#createClientFeatures()`.

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.jetbrains.annotations.NotNull;

public class MyLanguageServerFactory implements LanguageServerFactory {

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        return new LSPClientFeatures()
                .setCompletionFeature(new MyLSPCompletionFeature()) // customize LSP completion feature
                .setDiagnosticFeature(new MyLSPDiagnosticFeature()) // customize LSP diagnostic feature
                .setFormattingFeature(new MyLSPFormattingFeature()); // customize LSP formatting feature         
    }
}
```

| API                                                          | Description                                                                                                                        | Default Behaviour |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| Project getProject()                                         | Returns the project.                                                                                                               |                   |
| LanguageServerDefinition getServerDefinition()               | Returns the language server definition.                                                                                            |                   |
| boolean isServerDefinition(@NotNull String languageServerId) | Returns `true` if the given language server id matches the server definition and `false` otherwise.                                |                   |
| ServerStatus getServerStatus()                               | Returns the server status.                                                                                                         |                   |
| LanguageServer getLanguageServer()                           | Returns the LSP4J language server.                                                                                                 |                   |
| boolean keepServerAlive()                                    | Returns `true` if the server is kept alive even if all files associated with the language server are closed and `false` otherwise. | `false`           |
| boolean canStopServerByUser()                                | Returns `true` if the user can stop the language server in LSP console from the context menu and `false` otherwise.                | `true`            |
| boolean isEnabled(VirtualFile)                               | Returns `true` if the language server is enabled for the given file and `false` otherwise.                                         | `true`            |

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;

public class MyLSPClientFeatures extends LSPClientFeatures {

    @Override
    public boolean keepServerAlive() {
        // Kept alive the server even if all files associated with the language server are closed
        return true;
    }
}
```

```java
package my.language.server;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.jetbrains.annotations.NotNull;

public class MyLanguageServerFactory implements LanguageServerFactory {

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        return new MyLSPClientFeatures() // customize LSP general features
                .setCompletionFeature(new MyLSPCompletionFeature()) // customize LSP completion feature
                .setDiagnosticFeature(new MyLSPDiagnosticFeature()) // customize LSP diagnostic feature
                .setFormattingFeature(new MyLSPFormattingFeature()); // customize LSP formatting feature         
    }
}
```

## LSP Base Feature

| API                                                          | Description                                                                                     | Default Behaviour |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------|
| Project getProject()                                         | Returns the project.                                                                            |                   |
| LanguageServerDefinition getServerDefinition()               | Returns the language server definition.                                                         |                   |
| boolean isServerDefinition(@NotNull String languageServerId) | Returns true if the given language server id matches the server definition and false otherwise. |                   |
| ServerStatus getServerStatus()                               | Returns the server status.                                                                      |                   |
| LanguageServer getLanguageServer()                           | Returns the LSP4J language server.                                                              |                   |

### Disable a given LSP Feature

All `LSP*Feature` classes extend [AbstractLSPDocumentFeature](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/features/LSPAbstractFeature.java), which declares the `isEnabled` method that returns `true` by default:

```java
public boolean isEnabled(@NotNull PsiFile file) {
    return true;
}
```

By overriding this method, you can return `false` to disable a given LSP feature for your language server. This method is called before starting the language server.

### Supported LSP Feature

All `LSP*Feature` classes extend [AbstractLSPDocumentFeature](https://github.com/redhat-developer/lsp4ij/blob/main/src/main/java/com/redhat/devtools/lsp4ij/client/features/LSPAbstractFeature.java), which declares the `isSupported` method that uses the server capabilities:

```java
public boolean isSupported(@NotNull PsiFile file) {

}
```

This method is called after starting the language server to collect the LSP server capabilities.

### Project Access

If you need the `Project` instance in your LSP feature to retrieve the settings of the current project, you can use the `getProject()` getter method.

```java
public class MyLSPCodeLensFeature extends LSPCodeLensFeature {

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        return MySettings.getInstance(super.getProject()).isCodeLensEnabled();
    }

}
```

### Server Status

If you need to check the server status, you can use the `getServerStatus()` getter method.

```java
public class MyLSPCodeLensFeature extends LSPCodeLensFeature {

    @Override
    public boolean isEnabled(@NotNull PsiFile file) {
        // Here, code lens will be disabled if the language server is not started
        // (the LSP CodeLens will not force the start of the language server)
        var serverStatus = super.getServerStatus();
        return serverStatus == ServerStatus.starting || serverStatus == ServerStatus.started;
    }

}
```

## LSP CodeAction Feature

| API                                         | Description                                                                                                                                                                                                                        | Default Behaviour           |
|---------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)             | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)           | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |
| String getText(CodeAction codeAction)       | Returns the IntelliJ intention action text from the given LSP code action and `null` to ignore the code action.                                                                                                                    | `codeAction.getTitle()`     |
| String getFamilyName(CodeAction codeAction) | Returns the IntelliJ intention action family name from the given LSP code action.                                                                                                                                                  | Label of `CodeActionKind`   |
| String getText(Command command)             | Returns the IntelliJ intention action text from the given LSP command and `null` to ignore the command.                                                                                                                            | `command.getTitle()`        |
| String getFamilyName(Command command)       | Returns the IntelliJ intention action family name from the given LSP command.                                                                                                                                                      | "LSP Command"               |

## LSP CodeLens Feature

| API                                                                                                             | Description                                                                                                                                                                                                                        | Default Behaviour                  |
|-----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------|
| boolean isEnabled(PsiFile file)                                                                                 | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                             |
| boolean isSupported(PsiFile file)                                                                               | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability        |
| CodeVisionEntry createCodeVisionEntry(CodeLens codeLens, String providerId, LSPCodeLensContext codeLensContext) | Creates an IntelliJ `CodeVisionEntry` from the given LSP `CodeLens` and `null` otherwise (to ignore the LSP `CodeLens`).                                                                                                           |                                    |
| String getText(CodeLens codeLens)                                                                               | Returns the code vision entry text from the LSP `CodeLens` and `null` otherwise (to ignore the LSP `CodeLens`).                                                                                                                    | `codeLens.getCommand().getTitle()` |

Here is an example of code that avoids creating an IntelliJ `CodeVisionEntry` when the LSP `CodeLens` command is equal to `Run`:

```java
package my.language.server;

import com.redhat.devtools.lsp4ij.client.features.LSPCodeLensFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyLSPCodeLensFeature extends LSPCodeLensFeature {

    @Override
    @Nullable
    public String getText(CodeLens codeLens) {
        Command command = codeLens.getCommand();
        if ("Run".equals(command)) {
            return null;
        }
        return super.getText(codeLens);
    }

}
```

## LSP Color Feature

| API                                                                                  | Description                                                                                                                                                                                                                        | Default Behaviour           |
|--------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                                                      | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                                                    | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Completion Feature

| API                                                                                   | Description                                                                                                                                                                                                                        | Default Behaviour                                                                     |
|---------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| boolean isEnabled(PsiFile file)                                                       | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                                                                                |
| boolean isSupported(PsiFile file)                                                     | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability                                                           |
| LookupElement createLookupElement(CompletionItem item, LSPCompletionContext context)  | Create a completion lookup element from the given LSP completion item and context and null otherwise.                                                                                                                              |                                                                                       |
| void renderLookupElement(LookupElementPresentation presentation, CompletionItem item) | Update the given IntelliJ lookup element presentation with the given LSP completion item.                                                                                                                                          |                                                                                       |
| String getItemText(CompletionItem item)                                               | Returns the IntelliJ lookup item text from the given LSP completion item  and null otherwise.                                                                                                                                      | `item.getLabel()`                                                                     |
| String getTypeText(CompletionItem item)                                               | Returns the IntelliJ lookup type text from the given LSP completion item and null otherwise.                                                                                                                                       | `item.getDetail()`                                                                    |
| Icon getIcon(CompletionItem item)                                                     | Returns the IntelliJ lookup icon from the given LSP completion item and null otherwise.                                                                                                                                            | default icon from `item.getKind()`                                                    |
| boolean isStrikeout(CompletionItem item)                                              | Returns true if the IntelliJ lookup is strike out and false otherwise.                                                                                                                                                             | use `item.getDeprecated()` or `item.getTags().contains(CompletionItemTag.Deprecated)` |
| String getTailText(CompletionItem item)                                               | Returns the IntelliJ lookup tail text from the given LSP completion item and null otherwise.                                                                                                                                       | `item.getLabelDetails().getDetail()`                                                  |
| boolean isItemTextBold(CompletionItem item)                                           | Returns the IntelliJ lookup item text bold from the given LSP completion item and null otherwise.                                                                                                                                  | `item.getKind() == CompletionItemKind.Keyword`                                        |
| boolean isCaseSensitive(PsiFile file)                                                 | Determines whether or not completions should be offered in a case-sensitive manner.                                                                                                                                                | Case-insensitive.                                                                     |

## LSP Declaration Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Definition Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Diagnostic Feature

| API                                                                                                                   | Description                                                                                     | Default Behaviour |
|-----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------|
| boolean isEnabled(PsiFile file)                                                                                       | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.          | `true`            |
| void createAnnotation(Diagnostic diagnostic, Document document, List<IntentionAction> fixes, AnnotationHolder holder) | Creates an IntelliJ annotation in the given holder using the provided LSP diagnostic and fixes. |                   |
| HighlightSeverity getHighlightSeverity(Diagnostic diagnostic)                                                         | Returns the IntelliJ `HighlightSeverity` from the given diagnostic and `null` otherwise.        |                   |
| String getMessage(Diagnostic diagnostic)                                                                              | Returns the message of the given diagnostic.                                                    |                   |
| String getToolTip(Diagnostic diagnostic)                                                                              | Returns the annotation tooltip from the given LSP diagnostic.                                   |                   |
| ProblemHighlightType getProblemHighlightType(List<DiagnosticTag> tags)                                                | Returns the `ProblemHighlightType` from the given tags and `null` otherwise.                    |                   |

Here is an example of code that avoids creating an IntelliJ annotation when the LSP diagnostic code is equal to `ignore`:

```java
package my.language.server;

import com.intellij.lang.annotation.HighlightSeverity;
import com.redhat.devtools.lsp4ij.client.features.LSPDiagnosticFeature;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MyLSPDiagnosticFeature extends LSPDiagnosticFeature {

    @Override
    public @Nullable HighlightSeverity getHighlightSeverity(@NotNull Diagnostic diagnostic) {
        if (diagnostic.getCode() != null &&
                diagnostic.getCode().isLeft() &&
                "ignore".equals(diagnostic.getCode().getLeft())) {
            // return a null HighlightSeverity when LSP diagnostic code is equals
            // to 'ignore' to avoid creating an IntelliJ annotation
            return null;
        }
        return super.getHighlightSeverity(diagnostic);
    }

}
```

## LSP DocumentHighlight Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP DocumentLink Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP DocumentSymbol Feature

| API                                                                                       | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                                                           | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                                                         | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |
| StructureViewTreeElement getStructureViewTreeElement(DocumentSymbolData documentSymbol)   |                                                                                                                                                                                                                                    |                             |
| String getPresentableText(DocumentSymbol documentSymbol, PsiFile psiFile)                 |                                                                                                                                                                                                                                    |                             |
| Icon getIcon(DocumentSymbol documentSymbol, PsiFile psiFile)                              |                                                                                                                                                                                                                                    |                             |
| String getPresentableText(DocumentSymbol documentSymbol, PsiFile psiFile, boolean unused) |                                                                                                                                                                                                                                    |                             |
| void navigate(DocumentSymbol documentSymbol, PsiFile psiFile, boolean requestFocus)       |                                                                                                                                                                                                                                    |                             |                                                                                        | | |
| boolean canNavigate(DocumentSymbol documentSymbol, PsiFile psiFile)                       |                                                                                                                                                                                                                                    |                             |

Here is an example of code to customize the document symbol used in `Structure`:

```java
package com.redhat.devtools.lsp4ij.client;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPDocumentSymbolFeature;
import com.redhat.devtools.lsp4ij.features.documentSymbol.DocumentSymbolData;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MyLSPDocumentSymbolFeature extends LSPDocumentSymbolFeature {

    @Override
    public @Nullable StructureViewTreeElement getStructureViewTreeElement(DocumentSymbolData documentSymbol) {
        if ("ignore".equals(documentSymbol.getDocumentSymbol().getName())) {
            // Ignore document symbol with "ignore" name.
            return null;
        }
        return super.getStructureViewTreeElement(documentSymbol);
    }

    @Override
    public @Nullable Icon getIcon(@NotNull DocumentSymbol documentSymbol, @NotNull PsiFile psiFile, boolean unused) {
        if (documentSymbol.getKind() == SymbolKind.Class) {
            // Returns custom icon for 'Class' symbol kind
            return ...;
        }
        return super.getIcon(documentSymbol, psiFile, unused);
    }

    @Override
    public @Nullable String getPresentableText(@NotNull DocumentSymbol documentSymbol, @NotNull PsiFile psiFile) {
        if (documentSymbol.getKind() == SymbolKind.Class) {
            // Returns custom presentable text for 'Class' symbol kind
            return ...;
        }
        return super.getPresentableText(documentSymbol, psiFile);
    }

    @Override
    public @Nullable String getLocationString(@NotNull DocumentSymbol documentSymbol, @NotNull PsiFile psiFile) {
        if (documentSymbol.getKind() == SymbolKind.Class) {
            // Returns custom location string for 'Class' symbol kind
            return ...;
        }
        return super.getLocationString(documentSymbol, psiFile);
    }
}
```

## LSP FoldingRange Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Formatting Feature

| API                                                   | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                       | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                     | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |
| boolean isExistingFormatterOverrideable(PsiFile file) | Returns `true` if existing formatters are overrideable and `false` otherwise.                                                                                                                                                      | `false`                     |

Here is an example of code that allows executing the LSP formatter even if there is a specific formatter registered by an IntelliJ plugin:

```java
package my.language.server;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature;
import org.jetbrains.annotations.NotNull;

public class MyLSPFormattingFeature extends LSPFormattingFeature {

    @Override
    protected boolean isExistingFormatterOverrideable(@NotNull PsiFile file) {
        // By default, isExistingFormatterOverrideable returns false if it has a custom formatter with psi
        // returns true even if there is a custom formatter
        return true;
    }
}
```

## LSP Selection Feature

Integrates the LSP [`textDocument/selectionRange`](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_selectionRange) feature.

| API                                                   | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                       | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                     | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Hover Feature

| API                                                    | Description                                                                                                                                                                                                                        | Default Behaviour           |
|--------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                        | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                      | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |
| String getContent(MarkupContent content, PsiFile file) | Returns the HTML content from the given LSP Markup content and null otherwise.                                                                                                                                                     |                             |

## LSP Implementation Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP InlayHint Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP References Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Rename Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP SemanticTokens Feature

| API                                                                                                          | Description                                                                                                                                                                                                                        | Default Behaviour           |
|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)                                                                              | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file)                                                                            | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |
| TextAttributesKey getTextAttributesKey(@NotNull String tokenType, List<String> tokenModifiers, PsiFile file) | Returns the TextAttributesKey to use for colorization for the given token type and given token modifiers and null otherwise.                                                                                                       |                             |

```java
package my.language.server;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.client.features.LSPSemanticTokensFeature;
import org.jetbrains.annotations.NotNull;

public class MyLSPSemanticTokensFeature extends LSPSemanticTokensFeature {

    @Override
    public @Nullable TextAttributesKey getTextAttributesKey(@NotNull String tokenType,
                                                            @NotNull List<String> tokenModifiers,
                                                            @NotNull PsiFile file) {
        if ("myClass".equals(tokenType)) {
            TextAttributesKey myClass = ...
            return myClass;
        }
        if ("ignore".equals(tokenType)) {
            return null;
        }
        return super.getTextAttributesKey(tokenType, tokenModifiers, file);
    }
}
```

## LSP SignatureHelp Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP TypeDefinition Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Usage Feature

| API                               | Description                                                                                                                                                                                                                        | Default Behaviour           |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| boolean isEnabled(PsiFile file)   | Returns `true` if the LSP feature is enabled for the given file and `false` otherwise.                                                                                                                                             | `true`                      |
| boolean isSupported(PsiFile file) | Returns `true` if the LSP feature is supported for the given file and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the file and user with the LSP server capabilities. | Check the server capability |

## LSP Workspace Symbol Feature

| API                         | Description                                                                                                                                                                              | Default Behaviour                      |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| boolean isEnabled()         | Returns `true` if the LSP feature is enabled and `false` otherwise.                                                                                                                      | `true` when server is starting/started |
| boolean isSupported()       | Returns `true` if the LSP feature is supported and `false` otherwise. <br/>This supported state is called after starting the language server, which matches the LSP server capabilities. | Check the server capability            |
| boolean supportsGotoClass() | Returns `true` if the LSP feature is efficient enough to support the IDE's Go To Class action which may be invoked frequently and `false` otherwise.                                     | `false`                                |

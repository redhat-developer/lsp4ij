# Go Language Server

You can use the [Go language server](https://pkg.go.dev/golang.org/x/tools/gopls) to benefit from `Go` support:

![Go demo](../images/user-defined-ls/GoplsDemo.gif)

You can use it by following these instructions:

* [Download and install Go](https://go.dev/doc/install)
* [Read the "Installation" section](https://pkg.go.dev/golang.org/x/tools/gopls#section-readme) to install the Go language server **gopls**, basically, open a terminal and execute the following command:

**go install golang.org/x/tools/gopls@latest**

As the command will add **go** in your OS PATH, you will have to close and reopen your IntelliJ to update this PATH.

## Syntax coloration

Today LSP4IJ doesn't support `textDocument/semanticTokens` [Please vote at issue 238](https://github.com/redhat-developer/lsp4ij/issues/238), the demo
uses the [Go TextMate grammar](https://github.com/golang/vscode-go/tree/master/extension/syntaxes) 
that you can configure in IntelliJ via the `Editor / TextMate Bundles` settings.

You need to clone https://github.com/golang/vscode-go/tree/master/extension and reference this folder 
(which contains the [package.json](https://github.com/golang/vscode-go/blob/540e146da867f42298ccdac782e4e163fec16b0d/extension/package.json#L172))
to benefit from syntax coloration and language configuration (matching brackets, etc).
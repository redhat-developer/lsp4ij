You can use [Visual Studio Code's HTML language server](https://github.com/microsoft/vscode-html-languageservice) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download)
 * [Download and install Visual Studio Code](https://code.visualstudio.com/download)

Once Visual Studio Code is installed, it will store the HTML language server in:
> **${BASE_DIR}/resources/app/extensions/html-language-features/server/dist/node/htmlServerMain.js**

[LSP4IJ](https://github.com/redhat-developer/lsp4ij) tries to generate the proper **htmlServerMain.js** file path according to your OS, but you may have to adjust it:
 * Windows: `$USER_HOME$/AppData/Local/Programs/Microsoft VS Code/resources/app/...`
 * macOS: `/Applications/Visual Studio Code.app/Contents/Resources/app/...`
 * Linux: `/usr/share/code/resources/app/...`

The server talks LSP over its standard input and output (`--stdio`), or over a socket: set `Transport` to `Socket`
with `Start the language server, it connects to LSP4IJ`, and LSP4IJ replaces `--stdio` with `--socket=${port}` in the
command.

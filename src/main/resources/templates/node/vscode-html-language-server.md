You can use [Visual Studio Code's HTML language server](https://github.com/microsoft/vscode-html-languageservice) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download)
 * [Download and install Visual Studio Code](https://code.visualstudio.com/download)

Once Visual Studio Code is installed, it will store the CSS language server in:
> **${BASE_DIR}/resources/app/extensions/html-language-features/server/dist/node/htmlServerMain.js**

[LSP4IJ](https://github.com/redhat-developer/lsp4ij) tries to generate the proper **htmlServerMain.js** file path according to your OS, but you may have to adjust it.

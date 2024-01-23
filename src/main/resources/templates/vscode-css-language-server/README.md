You can use [Visual Studio Code's CSS language server](https://github.com/microsoft/vscode-css-languageservice) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download)
 * [Download and install Visual Studio Code](https://code.visualstudio.com/download)

Once Visual Studio Code is installed, it will store the CSS language server in: 
> **${BASE_DIR}/resources/app/extensions/css-language-features/server/dist/node/cssServerMain.js**

[LSP4IJ](https://github.com/redhat-developer/lsp4ij) tries to generate the proper **cssServerMain.js** file path according to your OS, but you may have to adjust it.

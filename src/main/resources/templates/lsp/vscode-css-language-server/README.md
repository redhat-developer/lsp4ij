You can use [Visual Studio Code's CSS language server](https://github.com/microsoft/vscode-css-languageservice) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download)
 * Click **OK**: when the language server starts, if its command doesn't start it, its installer installs [vscode-langservers-extracted](https://github.com/hrsh7th/vscode-langservers-extracted), which has the CSS language server, with **npm install vscode-langservers-extracted**.

The command starts the CSS language server where the installer puts it:
> **$USER_HOME$/.lsp4ij/lsp/vscode-langservers-extracted/node_modules/vscode-langservers-extracted/lib/css-language-server/node/cssServerMain.js**

If you installed vscode-langservers-extracted somewhere else, change the path of **cssServerMain.js** in the command.

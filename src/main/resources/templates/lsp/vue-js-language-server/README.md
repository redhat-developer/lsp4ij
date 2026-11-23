You can use [Vue Language Server](https://github.com/vuejs/language-tools/tree/master/packages/language-server) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download), with `npm`.
 * Select the `Vue.js Language Server` template and click **OK**. The first time the server starts, the installer of the
   template installs [@vue/language-server](https://www.npmjs.com/package/@vue/language-server) and
   [typescript](https://www.npmjs.com/package/typescript) into `$USER_HOME$/.lsp4ij/lsp/vue-js-language-server` with
   `npm install @vue/language-server typescript --force`, and sets the command to
   `"$USER_HOME$/.lsp4ij/lsp/vue-js-language-server/node_modules/.bin/vue-language-server" --stdio`
   (`vue-language-server.cmd` on Windows).

The template also fills:
 * Mappings > File name patterns: `*.vue` with the language id `vue`.
 * Initialization Options, with the TypeScript that the installer installs:
``` JSON
{
    "typescript": {
        "tsdk": "$USER_HOME$/.lsp4ij/lsp/vue-js-language-server/node_modules/typescript/lib"
    },
    "vue": {
        "hybridMode": false
    }
}
```

To install the server yourself, run `npm install -g @vue/language-server typescript`, keep the command of the template,
`vue-language-server --stdio` (`vue-language-server.cmd --stdio` on Windows), set `typescript.tsdk` to the `lib` folder
of that TypeScript, as `C:/Users/${user.name}/AppData/Roaming/npm/node_modules/typescript/lib` on Windows, and empty
the `Installer` tab before you click **OK**, so that LSP4IJ doesn't install the server.

You can use [Vue highlight](https://github.com/vuejs/vue-syntax-highlight) in the TextMate by following these instructions: 
 * Download and unzip in a folder
 * File -> Settings -> Editor -> TextMate Bundles
 * And press "+" `Select Path` to folder with highlight

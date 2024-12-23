You can use [Vue Language Server](https://github.com/vuejs/language-tools/tree/master/packages/language-server) by following these instructions:
 * [Install Node.js](https://nodejs.org/en/download)
 * [npm install -g @vue/language-server](https://www.npmjs.com/package/@volar/vue-language-server)
 * [npm install -g typescript](https://www.npmjs.com/package/typescript)

Configure, example on windows:
 * Server > Command > `vue-language-server.cmd --stdio` OR `node "C:/Users/${user.name}/AppData/Roaming/npm/node_modules/@vue/language-server/bin/vue-language-server.js" --stdio`
 * Mappings > File name patterns > press "+" > File name patterns like "`*.vue`" and language id like "`vue`"
 * Configuration: `{}`
 * Initialization Options, need set `${user.name}`:
``` JSON
{
    "typescript": {
        "tsdk": "C:/Users/${user.name}/AppData/Roaming/npm/node_modules/typescript/lib"
    },
    "vue": {
        "hybridMode": false
    }
}
```

You can use [Vue highlight](https://github.com/vuejs/vue-syntax-highlight) in the TextMate by following these instructions: 
 * Download and unzip in a folder
 * File -> Settings -> Editor -> TextMate Bundles
 * And press "+" `Select Path` to folder with highlight
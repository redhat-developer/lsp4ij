# ESLint Language Server

To enable [ESLint](https://eslint.org/) language support in your IDE, you can integrate the [ESLint Language Server](https://github.com/microsoft/vscode-eslint/tree/main/server) by following these steps:

![ESLint LS demo](../images/user-defined-ls/vscode-eslint-language-server/demo_ls.gif)

---

## Step 1: Install the Language Server

1. Open an `.js` file in your project.
2. Click on **Install ESLint Language Server**:

   ![Open file](../images/user-defined-ls/vscode-eslint-language-server/open_file.png)

3. This will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog) with `Ada Language Server` pre-selected:

   ![New Language Server Dialog](../images/user-defined-ls/vscode-eslint-language-server/new_language_server_dialog.png)

4. Click **OK**. This will create the `Ada Language Server` definition and start the installation:

   ![Installing Language Server](../images/user-defined-ls/vscode-eslint-language-server/language_server_installing.png)

5. Once the installation completes, the server should start automatically and provide Ada language support (autocomplete, diagnostics, etc.).

## Step 2: Configure ESLint

* Execute `npm install eslint` in your project root
* Create an `eslint-config.js` file on your root project like this:

 ```js
 export default [
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
    },
    rules: {
      'no-console': 'error',
    },
  },
];
```

* Create a `test.js` file like this:

```js 
const s = "foo";
console.log(s);
 ```

You should see an eslint error on console.

### Troubleshooting Installation

If the installation fails, you can customize the installation settings in the **Installer** tab,  
then click on the **Run Installation** hyperlink to reinstall the server:

![Installer tab](../images/user-defined-ls/vscode-eslint-language-server/installer_tab.png)

See [Installer descriptor](../UserDefinedLanguageServerTemplate.md#installer-descriptor) for more information.


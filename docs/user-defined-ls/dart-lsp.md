# Dart LSP

You can use the [Dart analysis server](https://github.com/dart-lang/sdk/blob/main/pkg/analysis_server/tool/lsp_spec/README.md),
which talks LSP, to get `Dart` support.

## Install the language server

1. [Install the Dart SDK](https://dart.dev/get-dart), which has the language server, and make sure `dart` is on your
   PATH. Close and reopen your IDE if you changed the PATH.

2. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server Protocol (LSP). For more information, refer to the [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

3. **Select `Dart LSP server` as the template** from the available options. This will populate the command field with
   `dart language-server`, and map `*.dart` files to the `dart` language id.

4. **Click OK** to apply the changes. You should now have Dart support enabled in your IDE.

The installer of the template downloads nothing: if `dart language-server` doesn't start and `dart` isn't found, it
shows a message with a link to install the Dart SDK.

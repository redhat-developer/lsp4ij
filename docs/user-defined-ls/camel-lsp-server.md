# Language Server for Apache Camel

To enable language support for [Apache Camel](https://camel.apache.org/) in your IDE, you can integrate the [Language Server for Apache Camel](https://github.com/camel-tooling/camel-language-server) by following these steps:

1. Open `File` -> `Settings`
2. Go to `Languages & Frameworks` -> `Language Servers (LSP4IJ)`
3. Click on the `+` button in the right panel, it will open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog)
4. Click `Choose template...`
5. Select `Camel Language Server`
6. Click `OK`
7. Click `OK`
8. Wait that the server is downloaded (a progress bar will appear in the bottom bar)
9. Enjoy completion, diagnostics, refactoring and more for your Apache Camel project!

The installer downloads the latest `camel-lsp-server` jar from
[Maven Central](https://central.sonatype.com/artifact/com.github.camel-tooling/camel-lsp-server) into
`$USER_HOME$/.lsp4ij/lsp/camel-lsp-server`, and sets the command to
`java -jar "$USER_HOME$/.lsp4ij/lsp/camel-lsp-server/camel-lsp-server-<version>.jar"`. You need Java, with `java` on
your PATH.

To install the server yourself, download the jar from Maven Central, set the command in the `Server` tab to
`java -jar "path/to/camel-lsp-server-<version>.jar"`, and empty the `Installer` tab before you click **OK**, so that
LSP4IJ doesn't install the server.

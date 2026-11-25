# Smart Data Lake Language Server

To edit the HOCON configuration files of [Smart Data Lake Builder](https://smartdatalake.ch/) in your IDE, you can
integrate the [Smart Data Lake Language Server](https://github.com/smart-data-lake/sdl-lsp) (`sdl-lsp`) by following
these steps.

## Install the language server

1. [Install Java](https://adoptium.net/), and make sure that `java` is on your PATH: the command starts the server
   with `java -jar`. Close and reopen your IDE if you changed the PATH.

2. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server
   Protocol (LSP). For more information, refer to the
   [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

3. **Select Smart Data Lake Language Server as the template** from the available options. This will populate the
   command field with `java -jar XXX-jar-with-dependencies.jar`, which the installer replaces, and the mappings, which
   map `*.conf` files to the `hocon` language id.

4. **Click OK** to apply the changes, and you should see the language server in the
   [LSP console](../UserGuide.md#lsp-console).

The first time the server starts, the installer of the template runs the command, and since the command of the
template doesn't start a server, it downloads the `sdl-lsp.jar` asset of the latest
[sdl-lsp release](https://github.com/smart-data-lake/sdl-lsp/releases) into the `.sdlb` folder of the project, and
sets the command to:

```
java -jar "$PROJECT_DIR$/.sdlb/sdl-lsp.jar"
```

The command takes the jar from the `.sdlb` folder of the project in which the language server starts, and the installer
runs at its first start only: in another project, click **Install if Needed** in the context menu of the language
server in the LSP console, which downloads the jar into the `.sdlb` folder of that project.

To install `sdl-lsp` yourself, download a `sdl-lsp*.jar` from the
[sdl-lsp releases](https://github.com/smart-data-lake/sdl-lsp/releases), set its path in the command, as
`java -jar "/path/to/sdl-lsp.jar"`, and empty the `Installer` tab before you click **OK**, so that LSP4IJ doesn't
install the server.

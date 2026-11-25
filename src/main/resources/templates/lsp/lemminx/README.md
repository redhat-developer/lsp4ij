You can use the [LemMinX XML language server](https://github.com/eclipse/lemminx) with LSP4IJ.

## Automatic Installation

The LemMinX template is listed only in a development IDE, which runs with `idea.plugin.in.sandbox.mode`, as `runIde`
does. At the first start of a language server created from it, as when you open an XML file, its installer downloads
the LemMinX uber jar from the [Eclipse download site](https://download.eclipse.org/justj/?file=lemminx/releases) and
configures the start command.
It downloads LemMinX 0.31.2 into `$USER_HOME$/.lsp4ij/lsp/lemminx/org.eclipse.lemminx-uber.jar`, where the command of
the template starts it. You need Java, with `java` on your PATH.

## Transport Modes

LemMinX supports two transport modes:

### stdio (default)

The default mode uses standard input/output:

```
java -classpath "path/to/org.eclipse.lemminx-uber.jar" org.eclipse.lemminx.XMLServerLauncher
```

### Socket (startAndConnect)

LemMinX can also run as a socket server. To use socket mode, set **Transport** to **Socket** with
**Start the language server, LSP4IJ connects to it**: LSP4IJ then changes the launcher class to
`XMLServerSocketLauncher` and adds the `--port` argument in the command:

```
java -classpath "path/to/org.eclipse.lemminx-uber.jar" org.eclipse.lemminx.XMLServerSocketLauncher --port ${port}
```

In socket mode, LemMinX starts a TCP server on the specified port (default 5008) and LSP4IJ connects to it.

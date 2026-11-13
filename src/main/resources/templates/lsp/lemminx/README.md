You can use the [LemMinX XML language server](https://github.com/eclipse/lemminx) with LSP4IJ.

## Automatic Installation

When you create a new language server from the LemMinX template, the installer automatically downloads the LemMinX uber jar
from the [Eclipse download site](https://download.eclipse.org/justj/?file=lemminx/releases) and configures the start command.

## Transport Modes

LemMinX supports two transport modes:

### stdio (default)

The default mode uses standard input/output:

```
java -classpath "path/to/org.eclipse.lemminx-uber.jar" org.eclipse.lemminx.XMLServerLauncher
```

### Socket (startAndConnect)

LemMinX can also run as a socket server. To use socket mode, change the launcher class
to `XMLServerSocketLauncher` and add the `--port` argument:

```
java -classpath "path/to/org.eclipse.lemminx-uber.jar" org.eclipse.lemminx.XMLServerSocketLauncher --port ${port}
```

In socket mode, LemMinX starts a TCP server on the specified port (default 5008) and LSP4IJ connects to it.

LSP4IJ is a new Free and Open-Source LSP client for JetBrains IDEs, compatible with both **community** and enterprise flavors.

It was originally a port of the [`Eclipse LSP4E`](https://github.com/eclipse/lsp4e) project, as part of the [`Quarkus Tools for IntelliJ`](https://github.com/redhat-developer/intellij-quarkus/) plugin, but has since then been extracted into a new standalone extension. In the process, the code has been partially rewritten to drastically improve stability, performance and developer experience.

It provides outstanding features such as: 
- The ability to configure LSP servers without developing a new plug-in (restricted to stdio-based connections for the moment).
- A set of API allowing third-party adopters to contribute LSP extensions to the JetBrains ecosystem
- a console view, allowing users to monitor and troubleshoot the communication between the IDE and the LSP servers.

The client already supports a significant amount of [features](https://github.com/redhat-developer/lsp4ij/blob/main/docs/LSPSupport.md) from the LSP 3.17 specification, and more are on their way.

If you found any bugs or can think of ideas for some great new features, please don’t hesitate to head over to our Github repository and open a ticket.

## Installation
LSP4IJ requires at least Java 17 and Intellij 2023.2 at the moment.

The LSP4IJ plugin is available in the stable channel of the [JetBrains Plugin Repository](https://plugins.jetbrains.com/plugin/23257-lsp4ij).
## Why LSP4IJ?

For several years our Red Hat team have implemented several language servers for XML, Quarkus and Qute which have been integrated into vscode via vscode-xml and vscode-microprofile, vscode-quarkus. These language servers have also been integrated into Eclipse IDE. In 2019, our mission was to provide tooling for Quarkus and notably we needed LSP support for IntelliJ. At that time we tried https://github.com/ballerina-platform/lsp4intellij which had advanced LSP support but which had the following limitations:
 * our Quarkus / Qute language server has a complicated mechanism which delegates part of the work to a Java component (JDT for Eclipse and Psi for IntelliJ) to avoid parsing Java classes twice (one parsing by the language server and one parsing by the IDE), we encountered numerous freezes.
 * ballerina manages certain LSP features without using IJ extension points (ex: hover, go to declaration)
 * difficulty tracing server status (starting, started, stopping, stopped), which makes it difficult to understand problems when the server cannot start.
 * ballerina works with a Timeout system which stops the LSP request after a defined time.

To integrate our language servers and Quarkus and Qute, we needed LSP support in IntelliJ:

 * which never freezes even if language server cannot be started.
 * which does not use a timeout system by managing the cancellation of LSP requests at the end (when the file is modified for example)
 * which provides a UI which allows you to see the state of the server language
 * an LSP console which displays LSP traces and server logs.

For these reasons, we developed LSP support within the IntelliJ Quarkus project. It took us several years to have LSP support that was mature and efficient. 
Seeing that this LSP support held up for Quarkus and Qute, we decided to extract this support into a project independent of Quarkus and Qute to provide generic LSP support.

In 2023 JetBrains provides LSP support, but only available in InteliJ Ultimate version, which we cannot use in IntelliJ Quarkus.

## LSP support

Explain quickly the LSP support and show a demo with go.

Show LSP console

## How to get started with LSP4IJ?

LSP4IJ provides an extension point which allows you to register your language server which we will explain in the rest of this article, but if you want to quickly test your language server with LSP4IJ without having to develop a plugin or if you do not have any skills in IntelliJ plugin development, LSP4IJ offers the possibility of integrating its language server (in stdio mode only for the moment) with a simple settings which defines:

 * the server language launch command
 * the association between the server language and the files which must be taken into account by the server language

TODO : 

 - explain step by step how to create user defined ls with TypeScript LS
 - show a demo with TypeScript LS
 - use macro to store the TypeScript LS in the project to share it with the team.
 - use Template to show how it is easy to create a user defined ls and speak about export

## How to integrate your language server in an IntelliJ plugin.

Defining a language server via settings allows you to integrate a language server in a few minutes into IntelliJ but if you want to correctly integrate a language server into IntelliJ, it is preferable to embed the language server in an INtelliJ plugin:

 * to avoid the user having to install the language server and its runtime (java, node, etc.). With a plugin, it is possible to embed the language server and develop an action to download another version of the language server, etc.
 * advanced language servers require implementing specific client-side commands, which is only possible with the development of an IntelliJ plugin.
 
TODO : 

 - show a sample of use of etension point 
 - set the link to dev guide

## Conclusion

FRED --->

## 

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
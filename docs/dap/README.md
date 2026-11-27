# Debug Adapter Protocol (DAP) documentation

LSP4IJ debugs a program with a debug adapter of the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/),
as VS Code does. A debug adapter comes from a `Debug Adapter Protocol` run/debug configuration that a user creates,
often from a template, or from a plugin that integrates it with the `debugAdapterServer` extension point.

- [DAP user guide](./UserGuide.md): the `Debug Adapter Protocol` run/debug configuration, breakpoints of every kind,
  stepping, stepping back, evaluating expressions, variables and their values, the debug console, the debugged
  process, Run In Terminal, disassembly, and the templates of debug adapters.
- [DAP developer guide](./DeveloperGuide.md): the `debugAdapterServer` extension point, how the debug adapter starts,
  launch and attach, a run/debug configuration and breakpoints of a plugin's own, and how variables and expressions
  are shown. Its API is experimental.
- [DAP support](./DAPSupport.md): which DAP events, requests and reverse requests LSP4IJ implements.

The [documentation index](../README.md#debug-adapter-templates) lists the pages of the debug adapter templates, with
what each one debugs. It is also the starting point of the other manuals of LSP4IJ, those of language servers among
them.

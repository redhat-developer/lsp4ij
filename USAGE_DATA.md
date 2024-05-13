## [Language Server Protocol Client for IntelliJ](https://github.com/redhat-developer/lsp4ij)

### Usage Data

LSP4IJ has opt-in telemetry collection, provided the [Red Hat Telemetry](https://github.com/redhat-developer/intellij-redhat-telemetry) plugin is installed.

## What's included in the LSP4IJ telemetry data

* LSP4IJ emits telemetry events when the extension starts and stops,
  which contain the common data mentioned on the
  [vscode-redhat-telemetry page](https://github.com/redhat-developer/intellij-redhat-telemetry/blob/main/USAGE_DATA.md).
* Events for added/removed servers. Includes:
  * whether the server was set manually
  * the server name, if it was selected from a template and was unchanged

# C# Language Server (OmniSharp)

To enable C# language support in your IDE, you can integrate [OmniSharp](https://github.com/OmniSharp/omnisharp-roslyn),
the C# language server based on [Roslyn](https://github.com/dotnet/roslyn), by following these steps. It serves C#
files (`*.cs`) and C# scripts (`*.csx`).

## Install the language server

1. [Install the .NET SDK](https://dotnet.microsoft.com/download), and make sure that `dotnet` is on your PATH: the
   command of the template starts OmniSharp with `dotnet`, and OmniSharp loads the projects with the MSBuild of the
   SDK, which it finds through the `dotnet` on the PATH, or through `DOTNET_ROOT`. OmniSharp 2.0.0 is built for .NET 10,
   and runs on .NET 10 or a later version. Close and reopen your IDE if you changed the PATH.

2. **Open the New Language Server Dialog**. This can usually be found under the IDE settings related to Language Server
   Protocol (LSP). For more information, refer to the
   [New Language Server Dialog documentation](../UserDefinedLanguageServer.md#new-language-server-dialog).

3. **Select C# Language Server (OmniSharp) as the template** from the available options. This will populate the
   command field with:

   ```
   dotnet "$USER_HOME$/.lsp4ij/lsp/omnisharp-roslyn/OmniSharp.dll" --languageserver
   ```

   where the installer of the template puts OmniSharp, and the mappings, which map `*.cs` and `*.csx` files to the
   `csharp` language id. IntelliJ IDEA has no C# file type, so the template maps file name patterns.

4. **Click OK** to apply the changes. You should now have C# language support enabled in your IDE, with OmniSharp
   integrated, and you should see the language server in the [LSP console](../UserGuide.md#lsp-console).

The first time the server starts, the installer of the template:

1. checks the .NET SDK with `dotnet --version`, in the folder of the project, so that a `global.json` there selects the
   SDK, as it does for OmniSharp. Without it, a notification says that OmniSharp needs the .NET SDK, and the installer
   downloads nothing; once you installed it, restart your IDE, or click **Install if Needed** in the context menu of the
   language server in the LSP console;
2. downloads the `omnisharp-<os>-<arch>.zip` asset of the latest
   [OmniSharp release](https://github.com/OmniSharp/omnisharp-roslyn/releases) for your OS and architecture, and
   extracts it into `$USER_HOME$/.lsp4ij/lsp/omnisharp-roslyn`, where the command finds `OmniSharp.dll`. If GitHub
   can't be reached, it downloads the release `v2.0.0` instead.

The assets which the installer selects are:

| OS      | Architecture  | Asset                       |
|---------|---------------|-----------------------------|
| Linux   | x64           | `omnisharp-linux-x64.zip`   |
| Linux   | arm64         | `omnisharp-linux-arm64.zip` |
| macOS   | Intel         | `omnisharp-osx-x64.zip`     |
| macOS   | Apple Silicon | `omnisharp-osx-arm64.zip`   |
| Windows | x64           | `omnisharp-win-x64.zip`     |
| Windows | x86           | `omnisharp-win-x86.zip`     |
| Windows | arm64         | `omnisharp-win-arm64.zip`   |

The releases also have `.tar.gz` assets, builds for musl (Alpine Linux) and Android (bionic), and the
`omnisharp.http-*` assets, of the HTTP server of OmniSharp, which doesn't talk LSP. To take one of the musl builds, as
`omnisharp-linux-musl-x64.zip`, change the `asset` of the `Installer` tab before you click **OK**, or later in the
`Installer` tab of the language server in the LSP console, and click **Run Installation** there.

### Install OmniSharp yourself

Download and extract the asset of your OS and architecture from the
[OmniSharp releases](https://github.com/OmniSharp/omnisharp-roslyn/releases), set the path of its `OmniSharp.dll` in
the command, as `dotnet "/path/to/omnisharp/OmniSharp.dll" --languageserver`, and empty the `Installer` tab before you
click **OK**, so that LSP4IJ doesn't install the server.

## The project

OmniSharp loads the solution (`*.sln`, `*.slnx`) or the projects (`*.csproj`) of the root folder that LSP4IJ gives it,
the folder of the IntelliJ project, and stops when the IDE, whose process id LSP4IJ gives it too, ends. In its language
server mode, OmniSharp counts lines and columns from 0, as LSP does, so its command needs none of the `-z` and
`--hostPID` options of its other mode.

## Options

OmniSharp reads its [options](https://github.com/OmniSharp/omnisharp-roslyn/wiki/Configuration-Options) from an
`omnisharp.json` file at the root of the project, and from `~/.omnisharp/omnisharp.json`
(`%USERPROFILE%\.omnisharp\omnisharp.json` on Windows). For instance, to keep the opening brace of a method on the line
of the method when it formats a file:

```json
{
  "FormattingOptions": {
    "NewLinesForBracesInMethods": false
  }
}
```

It also takes an option as an argument of its command, as `FormattingOptions:NewLinesForBracesInMethods=false` after
`--languageserver`. OmniSharp reads these options when it starts: restart the language server after you changed them.

The template sets no `Configuration`: OmniSharp 2.0.0 asks LSP4IJ for its `csharp` and `omnisharp` sections, but takes
its options from `omnisharp.json` and its command, so the same option in the `Configuration` tab, as
`omnisharp.FormattingOptions.NewLinesForBracesInMethods`, changes nothing.

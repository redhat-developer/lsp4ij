You can use [OmniSharp](https://github.com/OmniSharp/omnisharp-roslyn), the C# language server based on
[Roslyn](https://github.com/dotnet/roslyn), to edit C# files (`*.cs`) and C# scripts (`*.csx`).

1. [Install the .NET SDK](https://dotnet.microsoft.com/download), and make sure that `dotnet` is on your PATH: the
   command starts OmniSharp with `dotnet`, and OmniSharp loads the projects with the MSBuild of the SDK. OmniSharp 2.0.0
   runs on .NET 10 or later.

2. **Select C# Language Server (OmniSharp) as the template**. The command is
   `dotnet "$USER_HOME$/.lsp4ij/lsp/omnisharp-roslyn/OmniSharp.dll" --languageserver`, where the installer of the
   template puts OmniSharp.

3. **Click OK**. The first time the server starts, the installer of the template checks the .NET SDK with
   `dotnet --version`, downloads the `omnisharp-<os>-<arch>.zip` asset of the latest
   [OmniSharp release](https://github.com/OmniSharp/omnisharp-roslyn/releases) for your OS and architecture, and
   extracts it into `$USER_HOME$/.lsp4ij/lsp/omnisharp-roslyn`. Without the .NET SDK, it says so and downloads nothing.

OmniSharp loads the solution or the projects of the folder of the project. It reads its
[options](https://github.com/OmniSharp/omnisharp-roslyn/wiki/Configuration-Options) from an `omnisharp.json` file
there, or in `~/.omnisharp`, not from the `Configuration` tab.

To install OmniSharp yourself, extract the asset of your OS from the
[OmniSharp releases](https://github.com/OmniSharp/omnisharp-roslyn/releases), set the path of its `OmniSharp.dll` in the
command, and empty the **Installer** tab before you click **OK**, so that LSP4IJ doesn't install the server.

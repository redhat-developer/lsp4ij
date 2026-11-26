# User-defined language server template

LSP4IJ provides a [default user-defined language server template](./UserDefinedLanguageServer.md#default-template),
stored in the [templates folder](https://github.com/redhat-developer/lsp4ij/tree/main/src/main/resources/templates).

This section explains how to structure a user-defined template for your language server. A template is a folder with
these files, of which only `template.json` is required. LSP4IJ ignores any other file in the folder.

* [`template.json`](#template-descriptor): describes the language server, the command that starts it and its file
  mappings.
* `settings.json`: the `Configuration` of the `Server` tab of the `Configuration` tab, which LSP4IJ sends to the
  language server. `{}` without the file.
* `settings.schema.json`: the JSON Schema of `settings.json`, which the `Configuration` field uses.
* `initializationOptions.json`: the `Initialization Options`, which LSP4IJ sends in the `initialize` request. `{}`
  without the file.
* `experimental.json`: the `Experimental` field, the `experimental` client capabilities of the `initialize` request.
  `{}` without the file.
* `clientSettings.json`: the configuration of the `Client` tab of the `Configuration` tab, which sets how LSP4IJ
  itself handles the language server. `{}` without the file.
* [`installer.json`](./Installer.md): defines how to check, install, and configure the server.
* `workspaceFolderSettings.json`: the JSON of the
  [`Workspace Folders` tab](./UserDefinedLanguageServer.md#workspace-folders-tab). LSP4IJ reads it with the template,
  and the New Language Server dialog gives it to the language server that it creates.
* `README.md`: the instructions of the template, which the help button of the New Language Server dialog opens. The
  dialog shows that button only for a template with a `README.md`.

## How to Contribute a User-Defined Language Server Template

If you have [created a user-defined language server](./UserDefinedLanguageServer.md#new-language-server-dialog) 
and want to share it with others, you can contribute it to the official template repository used by LSP4IJ. Follow the steps below to submit your contribution.

### Steps to Contribute

1. **Edit the `template.json`**  
   After [exporting](./UserDefinedLanguageServer.md#exporting-templates) your language server, a folder will be generated containing `template.json`.
  - Add a unique `id` to identify your language server.
  - Adjust the `programArgs` section to support all relevant operating systems.
  - See the [template descriptor](#template-descriptor) section for more details.

2. **Submit a Pull Request**  
   Add your language server template folder in the [`/templates/lsp`](https://github.com/redhat-developer/lsp4ij/tree/main/src/main/resources/templates/lsp) directory.
  - Follow the structure of existing examples like [`/templates/lsp/typescript-language-server`](https://github.com/redhat-developer/lsp4ij/tree/main/src/main/resources/templates/lsp/typescript-language-server).

3. **Write the Documentation**  
   Provide documentation for your language server in the [`/docs/user-defined-ls`](https://github.com/redhat-developer/lsp4ij/tree/main/docs/user-defined-ls) folder.
  - Use existing docs as reference, for instance: [`/docs/user-defined-ls/typescript-language-server`](https://github.com/redhat-developer/lsp4ij/tree/main/docs/user-defined-ls/typescript-language-server.md).

4. **Reference it**  
   Add your language server in the [`Default template`](https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServer.md#default-template).

### ✅ Contribution Checklist

- [ ] `template.json` contains a unique `id` and correct `programArgs`
- [ ] Template is placed under `/templates/lsp/<your-language-server-name>`
- [ ] Documentation is placed under `/docs/user-defined-ls/<your-language-server-name>.md`
- [ ] Template is listed in the [Default template](https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServer.md#default-template)
- [ ] Pull Request includes only relevant files and follows the structure of existing examples
 
## Template descriptor

The template descriptor (`template.json`) is a JSON file used to define:

* the server ID and name
* the command to start the server
* the socket of a language server which doesn't talk LSP over its standard input and output (optional)
* the transports which the language server supports (optional)
* the file type mappings

All its fields are listed in [Fields](#fields).

#### Example `template.json`

```json
{
  "id": "typescript-language-server",
  "name": "TypeScript Language Server",
  "programArgs": {
    "default": "typescript-language-server --stdio",
    "windows": "cmd /C typescript-language-server.cmd --stdio"
  },
  "transports": [
    {
      "mode": "stdio",
      "argument": "--stdio"
    }
  ],
  "fileTypeMappings": [
    {
      "fileType": {
        "name": "JavaScript",
        "patterns": ["*.js"]
      },
      "languageId": "javascript"
    },
    {
      "fileType": {
        "name": "JavaScript-React",
        "patterns": ["*.jsx"]
      },
      "languageId": "javascriptreact"
    },
    {
      "fileType": {
        "name": "TypeScript",
        "patterns": ["*.ts"]
      },
      "languageId": "typescript"
    },
    {
      "fileType": {
        "name": "TypeScript-React",
        "patterns": ["*.tsx"]
      },
      "languageId": "typescriptreact"
    }
  ]
}
```

This language server talks LSP over its standard input and output only, so the template lists `stdio` in its
`transports` and has no `socket`. See [Socket](#socket) for a template with one.

#### Fields

| Field                 | Value                                                                                                                                                                                                                                                                  | Default                                                                                                        |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `id`                  | the id of the template. A language server created from the template keeps it: the banner which suggests the template for a file it supports stays away while such a language server exists, and the settings and the [export](./UserDefinedLanguageServer.md#exporting-templates) of the language server take the `transports` of the bundled template with this id. | none                                                                                                           |
| `name`                | the name of the language server                                                                                                                                                                                                                                        | none                                                                                                           |
| `url`                 | the page of the instructions, which the `Server` tab links as `Read the instructions`                                                                                                                                                                                  | `https://github.com/redhat-developer/lsp4ij/tree/main/docs/user-defined-ls/<id>.md`                            |
| `dev`                 | `true` lists the template only when the IDE runs with `idea.plugin.in.sandbox.mode`, as `runIde` does                                                                                                                                                                  | `false`                                                                                                        |
| `programArgs`         | the command which starts the language server, by OS, see [Command](#command). Required.                                                                                                                                                                                | none                                                                                                           |
| `workingDir`          | the working directory, which the New Language Server dialog gives the language server it creates. An [exported](./UserDefinedLanguageServer.md#exporting-templates) language server writes it.                                                                         | none: the language server runs in the project's directory, or in the root of each instance                    |
| `env`                 | the environment variables of the `Server` tab: `variables`, an object of names and values, and `includeSystemEnvironmentVariables`, `true` to pass the IDE's environment variables too                                                                                 | without `env`, no variables, and the IDE's are passed; `includeSystemEnvironmentVariables` is `false` when `env` doesn't set it |
| `fileTypeMappings`    | the files of the language server: each entry has a `fileType`, with the `name` of an IntelliJ file type, file name `patterns`, or both, and the `languageId` of the files                                                                                              | none                                                                                                           |
| `languageMappings`    | the files of the language server by IntelliJ language: each entry has the `language`, an IntelliJ language id, and the `languageId` of the files                                                                                                                       | none                                                                                                           |
| `socket`              | how LSP4IJ talks to a language server over a TCP socket, see [Socket](#socket)                                                                                                                                                                                         | the standard input and output, or the first mode of `transports` when it lists only socket modes               |
| `transports`          | the transports which the language server supports, see [Transports](#transports)                                                                                                                                                                                       | none                                                                                                           |
| `expandConfiguration` | `true` checks `Expand JSON when configuration is sent to the server`: a name with dots in the `Configuration`, as `"css.validate": true`, is sent as nested objects, `{"css": {"validate": true}}`                                                                      | `false`                                                                                                        |
| `disablePromotionFor` | plugin ids: while one of these plugins is installed, the banner which suggests the template for a file it supports doesn't show, as the Clojure LSP template does for `com.github.clojure-lsp`                                                                         | none                                                                                                           |
| `promotable`          | `false`: the banner which suggests templates for a file never suggests this one, as for the discontinued erlang-ls. The New Language Server dialog still lists it. LSP4IJ 0.30.1 and earlier don't know the field, and still suggest it                                 | `true`                                                                                                         |

A `fileType` with both a `name` and `patterns` gives two mappings, one for the file type and one for the patterns,
with the same `languageId`. The banner shows only in a file which IntelliJ opens as plain text, with TextMate, or with a
file type of your own from the `File Types` settings, and which no language server maps: one of yours, from a template
or not, or one of a plugin, also while it is disabled.

#### Command

`programArgs` holds the command by OS. LSP4IJ takes the value of the OS it runs on, `windows`, `mac` or `unix` (Linux
and other Unix systems), and `default` when there is none for it. The New Language Server dialog puts it in the
`Command` field.

LSP4IJ starts the command without a shell: it expands the macros, such as `$USER_HOME$`, and then splits the command at
its spaces. A part in double quotes stays one argument, so put a path in quotes, as the CSS template does, for a home
directory with a space:

```json
{
  "programArgs": {
    "default": "node \"$USER_HOME$/.lsp4ij/lsp/vscode-langservers-extracted/node_modules/vscode-langservers-extracted/lib/css-language-server/node/cssServerMain.js\" --stdio"
  }
}
```

Don't start the command with `sh -c` or `cmd /c` unless it needs shell syntax, see
[Environment variables](./UserDefinedLanguageServer.md#environment-variables). The command can also take the
placeholders `${instanceRoot}` and `${port}`, see [Macro syntax](./UserDefinedLanguageServer.md#macro-syntax).

#### Socket

`socket` is optional: without it, LSP4IJ talks to the language server over its standard input and output, unless its
[`transports`](#transports) lists only socket modes. A language server which talks LSP over a TCP socket instead has a
`socket`, whose `mode` is one of the [transports](./UserDefinedLanguageServer.md#transport):

* `attach`: LSP4IJ attaches to a language server which runs already and listens on `host` (the loopback address by
  default) and `port`.
* `startAndConnect`: LSP4IJ starts the language server, which listens on the port, and connects to it.
* `startAndAccept`: LSP4IJ listens on the port and starts the language server, which connects to it.

| Field  | Value                                                                          | Default                                         |
|--------|--------------------------------------------------------------------------------|-------------------------------------------------|
| `mode` | `attach`, `startAndConnect`, `startAndAccept`, or `stdio`                      | none: standard input and output, with a warning |
| `host` | a plain host name or IP address, used by `attach` only                         | the loopback address                            |
| `port` | a number from 0 to 65535; 0 lets LSP4IJ choose a free port (not with `attach`) | 0                                               |

A `mode` of `stdio` is the same as no `socket`. The command of a language server which LSP4IJ starts takes the port as
`${port}`, and a `port` of 0, or none, lets LSP4IJ choose a free one. With `attach`, LSP4IJ can't choose the port, so
the New Language Server dialog asks for one. A value LSP4IJ can't use, such as a missing or unknown `mode` or a `port`
that isn't a number from 0 to 65535, is shown as a warning in the New Language Server dialog, which says what LSP4IJ
does instead:

```json
{
  "id": "my-language-server",
  "name": "My Language Server",
  "programArgs": {
    "default": "node path/to/server.js --socket=${port}"
  },
  "socket": {
    "mode": "startAndAccept",
    "port": 0
  }
}
```

#### Transports

`transports` is optional. A template can list the transports which its language server supports, in `transports`,
each with its `mode` (`stdio` or one of the socket modes above) and the `argument` of the
command for it. The New Language Server dialog then helps with the transport: for a language server which lists only
`stdio`, a hint says that it talks LSP only over its standard input and output, for one which lists only socket modes, a
hint says that it talks LSP only over a socket, and for each socket mode which it lists, a hint below the socket modes
names the mode and the argument. Every transport can still be chosen, as the template may be the starting point of a
language server of your own which does talk over a socket: Socket, for a language server which lists only `stdio`,
gives the warning "The template says this language server talks LSP only over its standard input and output", which
blocks neither OK nor Apply in the settings of a language server created from the template.
Without `transports`, no hint shows.

A language server which lists only socket modes is the other way round: `Standard input and output (stdio)` gives the
warning "The template says this language server talks LSP only over a socket", which blocks neither OK nor Apply
either. Without a `socket`, the New Language Server dialog selects `Socket` for it, in the first mode of the list, with
an `Auto` port, or, for `attach`, no port yet. An [installer](./UserDefinedLanguageServer.md#transport) whose command
has no `${port}` leaves it on its socket, rather than switching it to its standard input and output, where it couldn't
talk at all; with an `Auto` port, a warning then says that its command needs `${port}`.

| Field      | Value                                                                 | Required |
|------------|-----------------------------------------------------------------------|----------|
| `mode`     | `stdio`, `attach`, `startAndConnect` or `startAndAccept`              | yes      |
| `argument` | the argument of the command for this mode, such as `--socket=${port}` | no       |

An entry with a missing or unknown `mode` is skipped, and shown as a warning in the New Language Server dialog. Without
an `argument`, the hint names only the mode. The hints follow the order of the list, and Socket starts in the first
socket mode of the list. The CSS language server, for instance, is started over its standard input and output, and also
talks over a socket it connects to:

```json
{
  "id": "vscode-css-language-server",
  "programArgs": {
    "default": "node .../cssServerMain.js --stdio"
  },
  "transports": [
    {
      "mode": "stdio",
      "argument": "--stdio"
    },
    {
      "mode": "startAndAccept",
      "argument": "--socket=${port}"
    }
  ]
}
```

When the user changes the transport, in the New Language Server dialog or in the settings of a language server created
from the template, the argument of the chosen transport replaces that of another listed transport in the command. The
arguments are matched as whole words, and the one which comes last in the command is replaced. A command with none of
the listed arguments stays as it is, and so does the command when LSP4IJ loads a template or saved settings. The
settings, and the installer of a language server created from the template, find the template by its `id` among the
bundled templates, so a language server created from a
[custom template](./UserDefinedLanguageServer.md#custom-template) gets this in the New Language Server dialog only.
The [export](./UserDefinedLanguageServer.md#exporting-templates) of a language server finds its template in the same
way, and writes the `transports` of the template: the New Language Server dialog gives an import of the export the
same hints, warning and arguments as the template. The export of a language server created from a custom template has
no `transports`.

#### Socket and transports

`socket` and `transports` answer two questions. `socket` is the transport which the New Language Server dialog
preselects, and which an [exported](./UserDefinedLanguageServer.md#exporting-templates) language server keeps.
`transports` lists what the language server supports: it changes what the dialog offers and hints, and the argument
that a change of the transport swaps in the command, and an exported language server has the `transports` of its
bundled template. A `socket` whose `mode` isn't one of the `transports` is still preselected, and the New Language
Server dialog warns: "The socket mode ... of the template isn't one of its transports". Keep them in line, for instance
with the `socket` mode as the socket mode of `transports`. The export of a language server which talks over a socket
that its template doesn't list, as one created from the TypeScript template, has both, and so gives this warning too
when it's imported.

## Installer descriptor

A template can have an installer, `installer.json`, which checks that its language server or debug adapter is
installed, installs it when it isn't, as with `npm install` or a download from GitHub, and can then set the command
that starts it. LSP4IJ runs it at the first start of a language server created from the template, before the first
debug session of a debug adapter, and when the user installs or reinstalls the server. Its format is described in
[Installer descriptor](./Installer.md).

The sections about the installer that this page had are on that page now:

* <a id="unique-url"></a>The `url` of a `download` task: [Unique URL](./Installer.md#unique-url).
* <a id="github-asset-download"></a>The `github` of a `download` task: [GitHub asset download](./Installer.md#github-asset-download).
* <a id="output-customization"></a>The `output` of a `download` task: [Output customization](./Installer.md#output-customization).

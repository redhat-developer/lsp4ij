# Installer descriptor

The installer descriptor (`installer.json`) is a file of a [template](./UserDefinedLanguageServerTemplate.md), and the
JSON of the [`Installer` tab](./UserDefinedLanguageServer.md#installer-tab) of a language server, used to:

* `check` the current installation,
* `run` the installation of the LSP/DAP server,
* and optionally `configure` the start command once installation is complete.

It is used:

* at the first start of a language server created with it, and before the first debug session of a debug adapter
  created with it in each IDE session: LSP4IJ runs the `check`, and the `run` if the check fails,
* when the user installs or reinstalls the server, or runs the installer from its `Installer` tab.

See [When the installer runs](#when-the-installer-runs).

You can find a sample in the [rust-analyzer installer tab](./images/user-defined-ls/rust-analyzer/installation_tab.png).

It should follow this structure:

```json5
{
  "id": "...",
  "name": "...",
  "executeOnStartServer": false,
  "properties": {
    // Declare values that the tasks take as ${name}...
  },
  "check": {
    // Declare tasks to check the installation...
  },
  "run": {
    // Declare tasks to run the installation...
  }
}
```

| Key                    | Value                                                                                                                                                           | Default                                                     |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `id`                   | not read by LSP4IJ                                                                                                                                              | none                                                        |
| `name`                 | the name of the installation, in its console and its notifications, as "Installation of `name` completed successfully."                                         | `Untitled`                                                  |
| `executeOnStartServer` | `true` checks the installation at the first start of the language server in each IDE session, not only at its first start ever                                  | `false`                                                     |
| `properties`           | strings, numbers or booleans, which the tasks take as `${name}`, see [Properties](#properties)                                                                  | none                                                        |
| `check`                | the task which checks that the server is installed                                                                                                              | none: the server counts as installed when it starts         |
| `run`                  | the task which installs the server                                                                                                                              | none                                                        |

`check` and `run` define starting [tasks](#task-descriptor), which can trigger other tasks using the `onFail` or `onSuccess` keys.

LSP4IJ provides built-in tasks:

* [showMessage](#task-showmessage) to show a notification, as when a tool that the server needs is missing,
* [exec](#task-exec) to execute a command (e.g., `npm install typescript-language-server`),
* [initialize](#task-initialize) to check that the server starts and answers the `initialize` request,
* [fileExists](#task-fileexists) to check that a file, as the installed server, exists,
* [download](#task-download) to retrieve a server from a given URL,
* [configureServer](#task-configureserver) to set the launch command based on the downloaded files,
* [replaceTemplateCommand](#task-replacetemplatecommand) to set the launch command while the server still has the
  command of its template.

LSP4IJ runs installers since 0.14.0, with almost all of their tasks and keys. `fileExists` works since 0.30.0, and
`initialize`, `replaceTemplateCommand` and a few keys since 0.30.2: see
[Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij) for what an earlier
version does with them.

To define custom tasks, you can contribute to the extension point `com.redhat.devtools.lsp4ij.installerTaskFactory`.

## Task descriptor

Typical task structure:

```json5
{
  "id": "...",
  "name": "...",
  // custom keys depending on task type
  "onFail": {
    // Task to execute on failure
  },
  "onSuccess": {
    // Task to execute on success
  }
}
```

These keys go inside the type of the task: a task is an object with one key, its type, such as `exec`, whose value
holds the keys of the task, as in this step of the check of the Go language server:

```json
{
  "exec": {
    "name": "Check Go runtime",
    "command": {
      "windows": "where go",
      "default": "which go"
    }
  }
}
```

LSP4IJ takes the first key of the object as the type of the task, and ignores the other keys next to it, so `onFail`
and `onSuccess` go inside the task, next to its `name`. A task of a type which no task factory registers is ignored, as
if the installer didn't have it.

* `name`: the name of the step, which the console of the installation shows as `- Step: <name>`.
* `id`: not used by the tasks of LSP4IJ.
* `onSuccess`: the task which runs when this one succeeds. This task then succeeds or fails as that one does.
* `onFail`: the task which runs when this one fails. This task then succeeds or fails as that one does, so an `onFail`
  can make up for what failed: the check of the Go language server tries the current command, and when it fails,
  checks that `go` is installed and tries the command again, or [shows a message](#task-showmessage) if `go` is missing.

To add a type of task, register an `InstallerTaskFactory`, such as a subclass of `InstallerTaskFactoryBase`, which
reads `id`, `name`, `onFail` and `onSuccess` for it. Its `type` is the key of the task in `installer.json`:

```xml
<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
  <installerTaskFactory type="myTask"
                        factoryClass="my.plugin.MyTaskFactory"/>
</extensions>
```

## Properties

The tasks take these values as `${name}`:

| Property                                  | Value                                                                                                                                  |
|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `${server.command}`                       | the command of the language server or debug adapter when the installation starts, but not in the `Installer` tab of a debug session   |
| the `properties` of the installer         | each value as written, such as `${serverDist}` for `"serverDist": "$USER_HOME$/.lsp4ij/lsp/clojure-lsp"` in the clojure-lsp installer |
| `${output.dir}` and `${output.file.name}` | where a [download](#task-download) put the server, for the tasks that run after it                                                    |

LSP4IJ replaces them in the `workingDir` of an `exec`, the `output.dir` of a `download` and the `command` of a
`configureServer`. In the `command` of an `exec`, it replaces only `${server.command}`, and only when it is the whole
command, as in `"command": "${server.command}"`. A property is replaced with its value as written: its macros, such as
`$USER_HOME$`, are expanded where the value is used.

## When the installer runs

A language server created with an installer, from a template or with the JSON of its `Installer` tab, checks its
installation at its first start, as when a file that it serves is opened:

1. LSP4IJ runs the `check` in the background, with a progress bar in the status bar.
2. If the check fails, LSP4IJ runs the `run`, and a notification says whether the installation completed successfully
   or failed.
3. LSP4IJ then starts the language server, also when the `run` failed.

LSP4IJ keeps in the settings of the language server that this first check ran, so that the later starts don't check
again, also after a restart of the IDE. An installation which failed, or whose `run` failed, isn't kept: the first start
of the language server in the next IDE session checks again, and installs the server if the check fails. One that the
user cancelled is kept, so that the installer doesn't run again by itself. With `"executeOnStartServer": true`, the
installation is checked at the first start of the language server in each IDE session. The bundled installers have
`false`.

The installer also runs on request:

* `Install if Needed`, in the context menu of the language server in the Language Servers view of the LSP console,
  runs the `check`, and the `run` if the check fails.
* `Reinstall`, in the same menu, runs the `run` without the `check`, as to get the latest version.
* The links of the `Installer` tab of the LSP console run the JSON of the tab: `Check Installation` the `check`,
  `Run Installation` the `run`, and `Check / Run Installation` the `check` and then the `run`, also when the check
  passes. A link whose task the installer doesn't have runs nothing, and says that there is nothing to check or to
  install. The `Installer` tab of the New Language Server dialog and of the settings page has no links.

An installer without a `check` counts as installed, at the start of the language server and with `Install if Needed`,
so only `Reinstall`, `Run Installation` and `Check / Run Installation` run its `run`.

`Install if Needed` and `Reinstall` stop the language server, and start it again after the installation if it ran. A
language server with [several instances](./UserDefinedLanguageServer.md#several-language-server-instances) has one
installer for all of them.

A debug adapter checks its installation before its first debug session in each IDE session, whatever
`executeOnStartServer` says: a console, `DAP Debugger (initializing)`, shows the `check`, and the `run` if the check
fails, and the debug session then starts. The `Installer` tab of the debug session has the links of the LSP console.
Since LSP4IJ 0.30.2, the installer of a debug adapter runs for the project of the run configuration, so an `exec` task
without `workingDir` runs in the directory of the project, as for a language server, and gets the tools that the project
selects, as with mise or asdf. An earlier version runs it in the directory of the IDE, as `/` for an IDE started from
the Dock.

The IDE's log, `idea.log` (*Help | Show Log in Finder*, *Explorer* or *Files*), has what an installer does, also when no
console shows it, as when the installer runs at the start of a language server whose console was never opened: the
start and the result of the installation and of each step, and the errors as warnings. With
`#com.redhat.devtools.lsp4ij.installation` in *Help | Diagnostic Tools | Debug Log Settings*, it also has the rest of
what the console shows, as the commands of the `exec` tasks and their output. The log has `<redacted>` instead of a
value which can be a secret, as a token, an `Authorization` header or the password of a URL.

## Task showMessage

Shows a notification, as in the `onFail` of a check which finds that a tool is missing. The check of the Go language
server shows this one when `go` isn't installed:

```json
{
  "showMessage": {
    "name": "Go Language Server install problem",
    "content": [
      "<a href=\"https://go.dev/doc/install\">Install Go</a> to install the Go Language Server (<code>gopls</code>) using <code>go install</code>.",
      "Make sure <code>go</code> is in your PATH, then restart your IDE."
    ]
  }
}
```

* `name`: the title of the notification, `Error` by default.
* `content`: the message, in HTML: a string, or an array of strings, each a paragraph. A link opens in the browser.
* `actions` (optional): an array of tasks, each an action of the notification, named by its `name`.

Without `actions`, the task shows the message as an error and fails, and the installation shows no notification of
its own. With `actions`, the task waits until the user clicks one of them, runs that task, and succeeds or fails as it
does. A notification closed without a click ends the task, which then fails.

## Task exec

Runs a command. The task succeeds when the command exits with 0 and writes nothing on its standard error, and, with
`outputMatches`, when its standard output matches it too.

| Key             | Value                                                                                                                                                                                                                                                                                                                                                                                                                                | Default                                   |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|
| `command`       | the command: a string, which LSP4IJ splits at its spaces outside double quotes (since LSP4IJ 0.30.0: an earlier version splits it at each space), or an array of its arguments; or an object of these by OS, as [`programArgs`](./UserDefinedLanguageServerTemplate.md#command). LSP4IJ expands the macros, such as `$USER_HOME$`, in each argument. Of the [properties](#properties), it replaces only a whole `${server.command}`. | none: the task fails                      |
| `workingDir`    | the directory in which the command runs, with properties and macros. LSP4IJ creates it if it doesn't exist.                                                                                                                                                                                                                                                                                                                          | the project's directory                   |
| `timeout`       | the time in milliseconds after which LSP4IJ stops the command. The task then succeeds.                                                                                                                                                                                                                                                                                                                                               | none: LSP4IJ waits until the command ends |
| `ignoreStderr`  | `true` lets the task succeed when the command writes on its standard error, as `npm install` does with its warnings                                                                                                                                                                                                                                                                                                                  | `false`                                   |
| `outputMatches` | a [regular expression](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html) that the standard output of the command must match somewhere for the task to succeed, as a version, see [Output of the command](#output-of-the-command). Since LSP4IJ 0.30.2                                                                                                                                       | none: any output                          |

The console of the installation shows the command after `> `, and its output. The command gets the environment
variables of the IDE and, since IntelliJ 2025.1, the environment of its working directory, as a language server does
(see [Directory-specific environment variables](./UserDefinedLanguageServer.md#directory-specific-environment-variables)),
and LSP4IJ looks the command up in the `PATH` of that environment, so that `npm` is the one of the directory's `node`.

Two `exec` tasks with the same `workingDir` run one after the other, also in two IDEs, as those of the CSS and ESLint
installers, which install one npm package into `$USER_HOME$/.lsp4ij/lsp/vscode-langservers-extracted/node_modules`.
An installation which started because its check failed, and waited, runs the check again once the other task is done,
and skips the command if the check then passes. `Reinstall` and the links of the `Installer` tab don't skip it.

Example using `npm` to install [typescript-language-server](https://github.com/typescript-language-server/typescript-language-server),
as the bundled template does:

```json
{
  "id": "typescript-language-server",
  "name": "TypeScript Language Server",
  "executeOnStartServer": false,
  "properties": {
    "workingDir" : "$USER_HOME$/.lsp4ij/lsp/typescript-language-server/node_modules"
  },
  "check": {
    "exec": {
      "name": "Trying current command",
      "command": "${server.command}",
      "timeout": 2000,
      "onSuccess": {
        "initialize": {
          "name": "Check that typescript-language-server starts"
        }
      }
    }
  },
  "run": {
    "exec": {
      "name": "Install TypeScript Language Server",
      "workingDir": "${workingDir}",
      "ignoreStderr": true,
      "command": {
        "windows": "npm.cmd install typescript-language-server typescript --force",
        "default": "npm install typescript-language-server typescript --force"
      },
      "onSuccess": {
        "configureServer": {
          "name": "Configure TypeScript Language Server command",
          "command": {
            "windows": "\"${workingDir}/.bin/typescript-language-server.cmd\" --stdio",
            "default": "\"${workingDir}/.bin/typescript-language-server\" --stdio"
          }
        }
      }
    }
  }
}
```

`npm install` runs in the `node_modules` folder of the server, because npm installs into the first folder, from its
working directory up, which has a `package.json` or a `node_modules` folder. Run in a new, empty folder, it would install
into a parent folder which has one, such as the home directory. Run in `node_modules`, it installs into its parent,
`$USER_HOME$/.lsp4ij/lsp/typescript-language-server`, so the command is in `${workingDir}/.bin`.

An `exec` whose command still runs at its `timeout` succeeds: that's meant for a command which waits, as a language
server does, but a command which hangs, waits for input, or fails later, succeeds too. That's why the `check` above
runs the [initialize](#task-initialize) task once the `exec` of `${server.command}` succeeds. Use `exec` alone for a
command which ends by itself, as `node --version`.

### Output of the command

**Since LSP4IJ 0.30.2.** An earlier version ignores `outputMatches`: the `exec` succeeds whatever the command
prints, see [Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij).

With `outputMatches`, the task succeeds only if the standard output of the command matches the regular expression
somewhere, as Java's `Matcher.find()` does, besides the exit code and the standard error. Use `(?m)` for `^` and `$` to
match at the start and the end of each line. A check can so ask for a version of a tool, which an exit code doesn't
say. For instance, `dotnet --list-sdks` prints a line per .NET SDK, as `10.0.100 [/usr/local/share/dotnet/sdk]`, or
`10.0.100 [C:\Program Files\dotnet\sdk]` on Windows, and this check asks for the .NET SDK 10 or later: a line which
starts with a major version of two digits or more, so not `8.0.404` nor `9.0.300`, and a space after the version, so
not a preview, as `10.0.100-rc.2.25502.107`:

```json
{
  "exec": {
    "name": "Check the .NET SDK 10 or later",
    "command": "dotnet --list-sdks",
    "outputMatches": "(?m)^[1-9]\\d+\\.\\d+\\.\\d+\\s",
    "onFail": {
      "showMessage": {
        "name": ".NET SDK 10 or later not found",
        "content": "Install the <a href=\"https://dotnet.microsoft.com/download\">.NET SDK</a> 10 or later."
      }
    }
  }
}
```

The regular expression is in a JSON string, so each `\` of the regular expression is written `\\`. When the output
doesn't match, the console says so, with the output, and the task fails. A command which still runs at its `timeout`
succeeds only if what it printed until then matches. A regular expression which isn't valid fails the task, without
running the command, and the console says why.

## Task initialize

**Since LSP4IJ 0.30.2.** An earlier version ignores the task: in the `onSuccess` of an `exec`, it leaves the result of
the `exec` as it is, see
[Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij).

`initialize` checks that the language server or debug adapter starts and talks its protocol. It starts the server as
LSP4IJ starts it, with its command, working directory, environment variables and transport, sends it `initialize` (the
LSP request for a language server, the DAP request for a debug adapter), and succeeds only if the server answers within
the `timeout`. An answer which is an error succeeds too, since it shows that the server runs, and the console says so.
A server which can't be started, ends, or doesn't answer in time fails the check, so that the server gets installed.

Then the task ends the server: it sends `shutdown` and `exit` to a language server, or `disconnect` to a debug adapter,
and destroys the process if it hasn't ended within 2 seconds. It destroys the process at once when the installation is
cancelled.

```json
{
  "check": {
    "exec": {
      "name": "Trying current command",
      "command": "${server.command}",
      "timeout": 2000,
      "onSuccess": {
        "initialize": {
          "name": "Check that ruby-lsp starts",
          "timeout": 10000
        }
      }
    }
  }
}
```

* `timeout` (optional, `30000` by default): how long the task waits for the answer to `initialize`, from the start of
  the server, in milliseconds.

The bundled installers run `initialize` as the `onSuccess` of the `exec` of `${server.command}` which they ran before,
rather than as the `check` itself, so that an earlier version still checks with the `exec` alone: as the `check`,
`initialize` would leave it no check, and the server would count as installed. The cost is that LSP4IJ starts the
server twice to check it: for the `timeout` of the `exec`, then for `initialize`.

The `initialize` request of a language server has the initialization options of its settings, and the project's
folder as root, without client capabilities. A language server which runs already, which LSP4IJ attaches to, isn't
touched: the task succeeds. A debug adapter is reached as a debug session reaches it: over a socket to the port of
`${port}` in its command, or to the address which it prints as its debug server ready pattern says, and otherwise over
its standard input and output.

## Task fileExists

**Since LSP4IJ 0.30.0**, with the globs of `file`. An earlier version ignores the task: as the whole `check`, it leaves
the installer no check, and the server counts as installed, see
[Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij).

Checks that a file exists, as the server that the `run` of the installer installs, so that the installation doesn't run
again once the server is there. Example: the check of clangd, whose archive has a folder named after the version of
clangd, as `clangd_19.1.2/`:

```json
{
  "check": {
    "fileExists": {
      "name": "Check if clangd is installed",
      "file": {
        "windows": "${serverDist}/clangd*/bin/clangd.exe",
        "default": "${serverDist}/clangd*/bin/clangd"
      },
      "onSuccess": {
        "replaceTemplateCommand": {
          "name": "Configure Clangd server command",
          "templateCommand": "clangd",
          "command": "\"${check.file}\""
        }
      }
    }
  }
}
```

* `file`: the path of the file, by OS and architecture as the `url` of a [download](#task-download), with the properties
  of the installer, as `${serverDist}`, and macros, as `$USER_HOME$`. Its segments can have the globs `*` and `?`, as
  `clangd*`, and `**` for any number of folders.
* The file found is `${check.file}`, since LSP4IJ 0.30.2, for the tasks that run after the check, as its `onSuccess`:
  the path as written in `file`, with the properties resolved, the macros kept and the globs replaced by the names
  found, as `$USER_HOME$/.lsp4ij/lsp/clangd_19.1.2/bin/clangd`, in the form of the `${output.dir}` of a download. Of
  several files that the globs match, it is the last in natural order, which compares the numbers in a name by their
  value, from the first segment on: `clangd_19.1.10/bin/clangd` after `clangd_19.1.2/bin/clangd`. A `fileExists` which
  finds no file removes `${check.file}`.

When the check finds the server installed, the `run` doesn't run, so a server created from the template would keep the
command of the template: a [replaceTemplateCommand](#task-replacetemplatecommand) in the `onSuccess` of the check gives
it the command that the download gives, from `${check.file}`.

## Task download

Structure of a `download` task:

```json5
{
  "download": {
    "id": "...",
    "name": "...",
    "url": "...",
    "output": {
      "dir": "...",
      "file": {
        "name": "...",
        "executable": true
      }
    },
    "onFail": {},
    "onSuccess": {}
  }
}
```

| Key      | Value                                                                                          | Default                                     |
|----------|------------------------------------------------------------------------------------------------|---------------------------------------------|
| `url`    | the URL of the file, one for all, or by OS and architecture, see [below](#unique-url)          | none                                        |
| `github` | the latest release of a GitHub repository, see [GitHub asset download](#github-asset-download) | none                                        |
| `maven`  | the latest version on Maven Central, see [Maven artifact download](#maven-artifact-download)   | none                                        |
| `output` | where the server goes, see [Output customization](#output-customization)                       | a new folder `$USER_HOME$/.lsp4ij/<random>` |

LSP4IJ takes the URL from `github` when it has an `owner`, a `repository` and an `asset`, or else from `maven` when it
has a `groupId` and an `artifactId`. When they give no URL, as when GitHub can't be reached, LSP4IJ falls back to `url`,
and the console says so. Without a URL for the OS and the architecture, the task fails.

LSP4IJ downloads the file into a temporary folder. An archive, such as a `.zip`, `.vsix`, `.tar`, `.tar.gz` or `.tgz`
file, is extracted into `output.dir`, and a `.gz` file that isn't a `.tar.gz` is decompressed there, as the one that
the rust-analyzer installer downloads on Linux and macOS. Any other file is copied there, under `output.file.name` or
its own name, in place of the file of an earlier installation. The name of the downloaded file, by which LSP4IJ also
recognizes an archive, is the last segment of the path of the URL, without its query or fragment.

Example: downloading a JAR and configuring the start command

```json
{
  "id": "camel-lsp-server",
  "name": "Camel Language Server",
  "check": {},
  "run": {
    "download": {
      "name": "Download Camel Language Server",
      "url": "https://repo1.maven.org/maven2/com/github/camel-tooling/camel-lsp-server/1.31.0/camel-lsp-server-1.31.0.jar",
      "output": {
        "dir": "$USER_HOME$/.lsp4ij/lsp/camel-lsp-server"
      },
      "onSuccess": {
        "configureServer": {
          "name": "Configure Camel Language Server command",
          "command": "java -jar \"${output.dir}/${output.file.name}\""
        }
      }
    }
  }
}
```

> `configureServer.command` supports variable substitution with `${output.dir}` and `${output.file.name}`.
> These values are resolved from the `output` object of the preceding `download` task.

In this example, `check` is empty, so the server counts as installed at the first start, and only `Reinstall`,
`Run Installation` and `Check / Run Installation` download it (see [When the installer runs](#when-the-installer-runs)).
The bundled [camel-lsp-server installer](../src/main/resources/templates/lsp/camel-lsp-server/installer.json) checks
first that the JAR is in its folder, and downloads the latest version from Maven Central, with this `url` as a
fallback.

### Unique URL:

```json
{
  "download": {
    "url": "<same URL for all OS>"
  }
}
```

### OS-specific URLs:

```json
{
  "download": {
    "url": {
      "windows": "<windows URL>",
      "default": "<linux/mac URL>"
    }
  }
}
```

you can find a sample with [clangd installer](../src/main/resources/templates/lsp/clangd/installer.json)

### OS and architecture-specific URLs:

```json
{
  "download": {
    "url": {
      "windows": {
        "x86_64": "<URL for win x86_64>",
        "x86": "<URL for win x86>"
      },
      "default": "<URL for linux/mac>"
    }
  }
}
```

A value by OS has the keys `windows`, `mac`, `unix` (Linux and other Unix systems) and `default`: LSP4IJ takes the key
of the OS it runs on, or else `default`. A value by architecture, inside one of them, has the keys of the CPU: LSP4IJ
takes IntelliJ's name for it, `x86_64`, `x86`, `arm64` or `arm32`, or else the `os.arch` of the JVM, such as `amd64` or
`aarch64`, or else `default`. So `x86_64` and `arm64` match on every OS. The `asset` of `github`, and the `dir` and the
`file.name` of `output`, take the same keys.

### GitHub asset download:

If DAP/LSP server can be downloaded from GitHub release like https://github.com/clojure-lsp/clojure-lsp/releases
you can use the `github` JSON object to download the proper asset: 

```json
{
  "download": {
    "github": {
      "owner": "clojure-lsp",
      "repository": "clojure-lsp",
      "prerelease": false,
      "asset": {
        "windows": "clojure-lsp-native-windows-amd64.zip",
        "unix": {
          "amd64": "clojure-lsp-native-linux-amd64.zip",
          "arm64": "clojure-lsp-native-linux-aarch64.zip"
        },
        "mac": {
          "aarch64": "clojure-lsp-native-macos-aarch64.zip",
          "x86_64": "clojure-lsp-native-macos-amd64.zip"
        }
      }
    }
  }
}
```

When the download occurs, LSP4IJ requests the 100 newest releases of the repository,
`https://api.github.com/repos/${owner}/${repository}/releases?per_page=100`: in the previous sample,
https://api.github.com/repos/clojure-lsp/clojure-lsp/releases?per_page=100. It takes the newest of them whose
`prerelease` is the `prerelease` of the task, and in it the first asset whose name matches `asset`, and downloads its
`browser_download_url`. When that release has no such asset, LSP4IJ doesn't look at older releases, and falls back to
`url`. It requests the releases of a repository once per IDE session. The rate limit of the GitHub API can stop it: a
[GitHub token](#github-token) raises it.

 * `owner` (required) the github owner.
 * `repository` (required) the github repository. 
 * `prerelease` (optional): `true` takes the newest prerelease, and `false`, the default, the newest release which isn't
   a prerelease.
 * `asset` (required) is used to select the proper file to download. Without `*`, the name of the asset must contain
   `asset`, ignoring case. With `*`, which stands for any text, the whole name must match, as for an asset whose name
   has a version or a timestamp.

```json
{
  "asset": "perllsp-*-x86_64-unknown-linux-gnu.tar.gz"
}
```

If there is an asset per OS and with architecture, you can write:

```json
{
  "asset": {
    "windows": "clojure-lsp-native-windows-amd64.zip",
    "unix": {
      "amd64": "clojure-lsp-native-linux-amd64.zip",
      "arm64": "clojure-lsp-native-linux-aarch64.zip"
    },
    "mac": {
      "aarch64": "clojure-lsp-native-macos-aarch64.zip",
      "x86_64": "clojure-lsp-native-macos-amd64.zip"
    }
  }
}
```

Samples:

* [clangd installer](../src/main/resources/templates/lsp/clangd/installer.json)
* [clojure-lsp installer](../src/main/resources/templates/lsp/clojure-lsp/installer.json)
* [rust-analyzer installer](../src/main/resources/templates/lsp/rust-analyzer/installer.json)

#### GitHub token

**Since LSP4IJ 0.30.2.** An earlier version sends no token, see
[Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij).

A `github` download asks the GitHub API, `api.github.com`, for the releases of the repository. Without a token, the
GitHub API allows 60 requests an hour per IP address, which all the IDEs and tools behind one address share, as on a
corporate network, a VPN or CI runners. When they are used up, LSP4IJ falls back to `url`, and a download without `url`
fails until the limit resets. With a GitHub token, the limit is 5,000 requests an hour.

Any token works, as a fine-grained personal access token, or the one that `gh auth token` prints: public repositories
need no scope or permission. The 5,000 requests an hour are per GitHub user: all the tokens of a user share them. In
GitHub Actions, the `GITHUB_TOKEN` of a workflow allows 1,000 requests an hour per repository.

A token doesn't make private repositories work: a `github` download supports public repositories only. With a token
that can read a private repository, the GitHub API lists its releases, but the token goes with the request to the GitHub
API only, so the assets of a private repository don't download.

##### Where LSP4IJ finds the token

LSP4IJ takes the first of these that has a token, at each request to the GitHub API:

1. **The environment variable `GITHUB_TOKEN`, or else `GH_TOKEN`**, the variable that the `gh` CLI reads. As with the
   `gh` CLI, the environment comes before the stored settings. The variable must be in the environment of the IDE, as
   for the commands of `exec` tasks: set in the shell that starts the IDE, or, for an IDE started from the Dock on macOS,
   in the profile of the login shell, as `~/.zprofile` or `~/.zshrc`, which the IDE reads when it starts. After setting
   it, restart the IDE.
2. **The GitHub token of the LSP4IJ settings**, in *Settings | Languages & Frameworks | Language Servers (LSP4IJ)*. It
   is kept in the IDE's Password Safe, for all the projects, never in a settings file, and the field doesn't show it.
   Emptying the field removes it.

Without any of them, the request has no token, as before.

![The GitHub token of the LSP4IJ settings](./images/user-defined-ls/GitHubTokenSetting.png)

On Windows and Linux, an IDE started from the Start menu or a desktop launcher doesn't see a token that a shell profile,
as `~/.bashrc`, exports. The setting needs no environment variable: it is the way to give a token to such an IDE.

LSP4IJ doesn't send the token with the download of the asset, nor to another host, even on a redirect. The console, the
notifications and the log never show it: they name where it comes from, as `the token of the environment variable
GITHUB_TOKEN` or `the token of the LSP4IJ settings`.

##### GitHub API rate limit

When a request to the GitHub API fails, the console says why:

| The console says                                     | Because                                                                                                                                                                                                      |
|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GitHub API rate limit reached`                      | the 60 requests an hour, or those of the token, are used up; the console says when the limit resets, and, without a token, where to set one                                                                  |
| `GitHub API refused the token ... (HTTP 401)`        | the token is invalid or has expired; the console says where it comes from, and how to replace it                                                                                                             |
| `... enforces SAML single sign-on`                   | the owner of the repository is an organization which enforces SAML single sign-on, and the token isn't authorized for it; the console shows the link which authorizes it, for an hour, when GitHub gives one |
| `the token lacks the permission ...`                 | the token, as a fine-grained one, hasn't the permissions that GitHub names                                                                                                                                   |
| `the repository ... was not found, or it is private` | the `owner` or the `repository` of the download is wrong, or the repository is private                                                                                                                       |

When GitHub refuses the token, as for these messages, or the rate limit of the token is reached, LSP4IJ tries the
request once again without the token, since the releases of a public repository don't need one, and the console says
so. This is the same for a token of the environment or of the settings.

When a download without `url` fails for one of these reasons, the notification of the failed installation says why
too, as the console does. At the rate limit, for example:

> Installation of `rust-analyzer` failed.
>
> GitHub API rate limit reached: 60 requests an hour (HTTP 403). It resets at 15:35:07 (local time). A GitHub token in
> Settings | Languages & Frameworks | Language Servers (LSP4IJ), or in the environment variable GITHUB_TOKEN of the
> IDE, raises the limit to 5,000 requests an hour.

![The notification of an installation which the rate limit of the GitHub API stopped](./images/user-defined-ls/GitHubRateLimitNotification.png)

The IDE's log, `idea.log` (*Help | Show Log in Finder*, *Explorer* or *Files*), has a line for each download, with its
URL, where the URL comes from and how long it took, and a warning for a rate limit, a refused token or a failed
download. With `#com.redhat.devtools.lsp4ij.installation` in *Help | Diagnostic Tools | Debug Log Settings*, it also
has each request to the GitHub API, with its status, its rate limit and how long it took. The log never has the token.

### Maven artifact download

If the DAP/LSP server is published on [Maven Central](https://search.maven.org), you can use the `maven` object in your installer JSON to download the corresponding artifact:

```json
{
  "download": {
    "name": "Download Camel Language Server",
    "maven": {
      "groupId": "com.github.camel-tooling",
      "artifactId": "camel-lsp-server"
    }
  }
}
```

When the download occurs:

1. A request is made to the following Maven Central API endpoint to find the latest artifact:
   ```
   https://search.maven.org/solrsearch/select?q=g:%22com.github.camel-tooling%22%20AND%20a:%22camel-lsp-server%22&rows=20&wt=json
   ```
2. The `latestVersion` of the first result is the version to download.
3. The download URL is then computed as:
   ```
   https://repo1.maven.org/maven2/{groupId with slashes}/{artifactId}/{version}/{artifactId}-{version}.jar
   ```

For example, if the groupId is `com.github.camel-tooling` and the artifactId is `camel-lsp-server`, the computed download URL might be:
```
https://repo1.maven.org/maven2/com/github/camel-tooling/camel-lsp-server/1.5.0/camel-lsp-server-1.5.0.jar
```

LSP4IJ requests the version once per IDE session. When Maven Central gives none, as when it can't be reached, the task
falls back to `url`.

**Fields:**

- `groupId` (required): Maven group ID.
- `artifactId` (required): Maven artifact ID.

**Sample:**

- [Camel Language Server installer](../src/main/resources/templates/lsp/camel-lsp-server/installer.json)

#### Output customization

You can customize the output directory and executable name, as the
[rust-analyzer installer](../src/main/resources/templates/lsp/rust-analyzer/installer.json) does, whose `serverDist`
property is `$USER_HOME$/.lsp4ij/lsp/rust-analyzer`:

```json5
{
  "output": {
    "dir": "${serverDist}",
    "file": {
      "name": {
        "windows": "rust-analyzer.exe",
        "unix": {
          "x86_64": "rust-analyzer-x86_64-unknown-linux-gnu",
          "arm64": "rust-analyzer-aarch64-unknown-linux-gnu"
        },
        "mac": {
          "x86_64": "rust-analyzer-x86_64-apple-darwin",
          "arm64": "rust-analyzer-aarch64-apple-darwin"
        }
      },
      "executable": true
    }
  }
}
```

* `dir`: base folder where the downloaded file will be saved or extracted, by OS and architecture, with
  [properties](#properties) and macros. LSP4IJ creates it if it doesn't exist. Without it, LSP4IJ creates a new folder
  in `$USER_HOME$/.lsp4ij` at each download.
  The value is available as `${output.dir}` for use in other tasks, with the properties replaced and the macros kept,
  as `$USER_HOME$/.lsp4ij/lsp/rust-analyzer`.
  An archive with a single root folder, as `perllsp-0.17.0-x86_64-unknown-linux-gnu/`, is extracted with that folder,
  which is then added to `${output.dir}`.
* `stripRootFolder` (optional, `false` by default): extracts the content of the single root folder of the archive into
  `dir` itself, so the server is found at the same place from one version to the next, as the
  [perl-lsp installer](../src/main/resources/templates/lsp/perl-lsp/installer.json) does. Since LSP4IJ 0.30.2: an
  earlier version ignores it, and extracts the archive with its root folder, so the server isn't where a check or a
  command that looks in `dir` itself expects it.
* `file.name`: name of the executable file (registered as `${output.file.name}`), by OS and architecture. When it is
  set, the task fails if the file isn't in `${output.dir}` after the download. A file which isn't an archive is saved
  under this name, and without it, under its own name, the last segment of the path of the URL without its query or
  fragment, which is then `${output.file.name}`.
* `file.executable` (optional, `false` by default): `true` makes the file of `file.name` executable.

## Task configureServer

Used to set the server launch command:

```json5
{
  "configureServer": {
    "name": "Configure Camel Language Server command",
    "command": "java -jar \"${output.dir}/${output.file.name}\""
  }
}
```

* `command`: the command, by OS and architecture, with the properties of the installer and of the tasks before it, as
  `${output.dir}` and `${output.file.name}` of a [download](#task-download), or `${check.file}` of a
  [fileExists](#task-fileexists). A command with `${check.file}` that no `fileExists` found a file for is an error, and
  the command of the server stays as it is.

## Task replaceTemplateCommand

**Since LSP4IJ 0.30.2.** An earlier version ignores the task: the server keeps the command of its template, and the
check still passes, see [Compatibility with earlier versions of LSP4IJ](#compatibility-with-earlier-versions-of-lsp4ij).

Sets the server launch command, as [configureServer](#task-configureserver) does, but only while the server has the
command of its template. In the `onSuccess` of a check, which also runs from *Check installation* in the settings, it
gives a server created from the template the command of the file that the check found, and leaves a command that the
user or a download gave the server as it is:

```json5
{
  "replaceTemplateCommand": {
    "name": "Configure Clangd server command",
    "templateCommand": "clangd",
    "command": "\"${check.file}\""
  }
}
```

* `templateCommand`: the command of the template, by OS and architecture, as its `programArgs`, or the `launch` of a
  debug adapter, compared as written. While the server has another command, the task leaves it as it is and succeeds.
  On an OS without a `templateCommand`, the task never changes the command.
* `command`: the command that replaces it, as the `command` of a `configureServer`.

## Compatibility with earlier versions of LSP4IJ

LSP4IJ runs installers since 0.14.0, which has almost all the tasks and keys of this page. An earlier version ignores
`installer.json`. These came later, and their sections say since which version:

| Since  | Task or key                                                                                                                                  | What an earlier version does                                                                                                                                                     |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0.30.0 | the [`fileExists`](#task-fileexists) task, with the globs `*`, `?` and `**` of its `file`                                                    | ignores it: as the whole `check`, it leaves the installer no check, so the server counts as installed and is never installed by itself                                           |
| 0.30.0 | the `command` of an [`exec`](#task-exec) as a string with an argument in double quotes                                                       | splits the string at each space, also inside the double quotes, which stay in the arguments: give such a command as an array of its arguments                                    |
| 0.30.2 | [`outputMatches`](#output-of-the-command) of an `exec`                                                                                       | ignores it: the `exec` succeeds whatever the command prints                                                                                                                      |
| 0.30.2 | the [`initialize`](#task-initialize) task                                                                                                    | ignores it: in the `onSuccess` of an `exec`, it leaves the result of the `exec` as it is                                                                                         |
| 0.30.2 | the [`replaceTemplateCommand`](#task-replacetemplatecommand) task, with its `templateCommand`                                                | ignores it: the server keeps the command of its template, and the check still passes                                                                                             |
| 0.30.2 | `${check.file}`, the file that a [`fileExists`](#task-fileexists) found                                                                      | doesn't replace it: a `configureServer` would give the server a command with `${check.file}` in it                                                                               |
| 0.30.2 | [`stripRootFolder`](#output-customization) of the `output` of a `download`                                                                   | ignores it: it extracts the archive with its root folder, which it adds to `${output.dir}`, so the server isn't where a check or a command that looks in `dir` itself expects it |
| 0.30.2 | the [GitHub token](#github-token): `GITHUB_TOKEN` or `GH_TOKEN`, and the token of the LSP4IJ settings                                        | sends no token: the GitHub API allows 60 requests an hour per IP address                                                                                                         |

### What an earlier version does with what it doesn't know

Every version from 0.14.0 to 0.30.1 treats a task or a key that it doesn't know in the same way:

* A task of a type that it doesn't know is no task:
  * in an `onSuccess` or an `onFail`, the task which has it succeeds or fails as it would without it;
  * as the whole `check`, it leaves the installer no check, so the server counts as installed, and LSP4IJ never
    installs it by itself;
  * as the whole `run`, it leaves the installer nothing to install.
* A key that it doesn't know is ignored, as is any key that LSP4IJ doesn't read.

### When it matters

An earlier version of LSP4IJ reads:

* a template that you [export](./UserDefinedLanguageServer.md#exporting-templates) and share with someone whose LSP4IJ
  is earlier;
* the `installer.json` of a plugin which bundles one and supports several versions of LSP4IJ;
* the installer that a language server keeps in the settings of the IDE, from the template it was created from, when
  the IDE goes back to an earlier LSP4IJ.

A template bundled with LSP4IJ ships with the LSP4IJ which reads it, but a language server created from it is in the
last case.

So put a task that an earlier version doesn't know in the `onSuccess` of one that it knows, as the bundled installers
put `initialize` after the `exec` of `${server.command}`, and `replaceTemplateCommand` after a `fileExists`: an earlier
version then decides with the task that it knows alone, while as the whole `check`, the newer task would leave it no
check.

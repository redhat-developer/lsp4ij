# Candor Language Server

You can use the [Candor language server](https://candor.poly.io) to see the side effects of each function where the
code is:

* **CodeLens** on every function with effects, as `⚡ Db, Net · blast radius 12`: the effects it performs, also through
  the functions it calls, and how many functions are affected if it changes;
* **Diagnostics**: the architecture policy of the repository, as "the domain layer does no I/O", checked as you edit;
  a violation is shown at the function;
* **Hover**: the call chain through which a function gets an effect from another one.

The template maps `*.java`, `*.kt`, `*.kts`, `*.ts`, `*.tsx`, `*.js`, `*.jsx`, `*.mjs`, `*.rs` and `*.swift` files to
the server.

## Produce a report

The server shows a Candor report; it doesn't analyse the source itself. Produce the report with a Candor engine
(JVM bytecode, TypeScript, Rust or Swift, see [candor.poly.io](https://candor.poly.io)), for instance:

* JVM: `jbang candor@tombaldwin/candor-java target/classes --json .candor/report.json`
* TypeScript and JavaScript: `npx -y candor-ts src --out .candor/report`

The server reads the report at `<project>/.candor/report` and the policy from the checked-in `.candor/config`, so it
needs no settings per project.

## Install the language server

1. [Install Node.js](https://nodejs.org/en/download), with `npm`.
2. Open the [New Language Server Dialog](../UserDefinedLanguageServer.md#new-language-server-dialog), select the
   `Candor Language Server` template, and click **OK**.
3. The first time the server starts, the installer of the template installs the `candor-ts` npm package, which has
   the `candor-lsp` language server, into `$USER_HOME$/.lsp4ij/lsp/candor-lsp` with `npm install candor-ts`, and sets
   the command to `"$USER_HOME$/.lsp4ij/lsp/candor-lsp/node_modules/.bin/candor-lsp"` (`candor-lsp.cmd` on Windows).

To install the server yourself, run `npm install -g candor-ts`, keep the command of the template, `candor-lsp`
(`cmd /C candor-lsp.cmd` on Windows), and empty the `Installer` tab before you click **OK**, so that LSP4IJ doesn't
install the server.

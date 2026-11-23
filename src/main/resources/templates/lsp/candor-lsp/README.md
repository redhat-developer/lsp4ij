You can use the [candor language server](https://candor.poly.io) to see each function's **side-effect
map** where the code is:

 * **CodeLens** on every effectful function: `⚡ Db, Net · blast radius 12` — the effects it (transitively)
   performs and how many functions are affected if it changes;
 * **Diagnostics**: the repo's architecture policy (e.g. "the domain layer does no I/O") checked live —
   a violation is a squiggle at the offending function;
 * **Hover**: effect provenance — the call chain through which an inherited effect reaches its source.

The server renders a **candor report** — it does not analyse source itself. Produce one with any candor
engine (JVM bytecode / TypeScript / Rust / Swift; see [candor.poly.io](https://candor.poly.io)), e.g.:

**jbang candor@tombaldwin/candor-java target/classes --json .candor/report.json**   (JVM)

**npx -y candor-ts src --out .candor/report**   (TypeScript/JavaScript)

Setup:
* [Install Node.js](https://nodejs.org/en/download), with `npm`.
* Click **OK**. The first time the server starts, the installer of the template installs the candor-ts package, which
  has the `candor-lsp` language server, into `$USER_HOME$/.lsp4ij/lsp/candor-lsp` with **npm install candor-ts**, and
  sets the command to `"$USER_HOME$/.lsp4ij/lsp/candor-lsp/node_modules/.bin/candor-lsp"` (`candor-lsp.cmd` on
  Windows).

To install the server yourself, open a terminal and execute **npm install -g candor-ts**, keep the command of the
template, `candor-lsp` (`cmd /C candor-lsp.cmd` on Windows), and empty the **Installer** tab before you click **OK**,
so that LSP4IJ doesn't install the server.

The server resolves the report at `<project>/.candor/report` and the policy from the checked-in
`.candor/config` — no per-project server configuration is needed.

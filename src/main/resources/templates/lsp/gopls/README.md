You can use the [Go language server](https://pkg.go.dev/golang.org/x/tools/gopls) by following these instructions:
* [Download and install Go](https://go.dev/doc/install). As the installation adds **go** to your OS PATH, you will have to close and reopen your IDE to update this PATH.
* The installer of the template installs the Go language server **gopls** the first time the server starts, with:

**go install golang.org/x/tools/gopls@latest**

`go install` puts `gopls` in the `bin` folder of your Go path (`go env GOPATH`, `$HOME/go` by default), which must be on the PATH of your IDE. Otherwise, set the command to the full path of `gopls`.

To install gopls yourself, [read the "Installation" section](https://pkg.go.dev/golang.org/x/tools/gopls#section-readme), or run the same command in a terminal.

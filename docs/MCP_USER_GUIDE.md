# LSP4IJ MCP Server - User Guide

## Overview

LSP4IJ now includes an **MCP (Model Context Protocol) Server** that exposes language servers managed by IntelliJ as tools for AI assistants like Claude.

### What is MCP?

Model Context Protocol (MCP) is an open protocol created by Anthropic that enables AI assistants to interact with external tools and data sources. LSP4IJ's MCP server allows Claude and other MCP clients to use language servers already running in your IntelliJ IDE.

### Key Benefits

- ✅ **No process duplication**: Reuses language servers already launched by IntelliJ
- ✅ **Hot context**: Diagnostics, opened files, and project state already available
- ✅ **Preserves IDE logic**: Uses existing `LanguageClientImpl` with all custom features (e.g., Qute LS data model)
- ✅ **Zero configuration**: Works with any language server supported by LSP4IJ

## Quick Start

### 1. Start the MCP Server in IntelliJ

Open your project in IntelliJ and go to:
```
Tools → Start MCP Server
```

The server will start and wait for MCP client connections via stdio.

### 2. Configure Claude Code

Add the following to your `.claude/settings.json`:

```json
{
  "mcpServers": {
    "lsp4ij": {
      "command": "idea",
      "args": ["mcp-server", "--project", "${workspaceFolder}"]
    }
  }
}
```

> **Note**: The exact command depends on how you launch IntelliJ from the command line. Adjust `idea` to match your installation (e.g., `idea.bat` on Windows, `/Applications/IntelliJ\ IDEA.app/Contents/MacOS/idea` on macOS).

### 3. Use in Claude

Once configured, Claude can interact with your language servers:

```
You: "Claude, list all language servers in my project"

Claude: [uses lsp-listServers]
✅ Found 3 language servers:
   - rust-analyzer (status: started)
   - typescript-language-server (status: started)
   - kotlin-language-server (status: started)

You: "Find all usages of MyClass"

Claude: [uses lsp/getWorkspaceSymbols then workspace/references]
✅ Found 15 usages across 5 files...
```

## Available MCP Tools

### `lsp-listServers`

Lists all started language servers in the project with their capabilities.

**Parameters**: None

**Example Output**:
```json
{
  "servers": [
    {
      "id": "rust-analyzer",
      "displayName": "Rust Analyzer",
      "status": "started",
      "capabilities": {
        "documentSymbol": true,
        "codeAction": true,
        "executeCommand": true,
        "commands": [
          "rust-analyzer.expandMacro",
          "rust-analyzer.viewCrateGraph"
        ]
      }
    }
  ],
  "count": 1
}
```

### `lsp-executeCommand`

Executes a command on a specific language server.

**Parameters**:
- `serverId` (string, required): The language server ID (e.g., "rust-analyzer")
- `command` (string, required): The command name to execute
- `arguments` (array, optional): Command arguments

**Example**:
```json
{
  "serverId": "rust-analyzer",
  "command": "rust-analyzer.expandMacro",
  "arguments": [
    {"uri": "file:///path/to/main.rs", "position": {"line": 10, "character": 5}}
  ]
}
```

**Example Output**:
```json
{
  "success": true,
  "result": {
    "expansion": "println!(\"Hello, world!\");"
  }
}
```

## Use Cases

### 1. Code Analysis

**User**: "What are all the errors in my Rust project?"

**Claude**:
1. Calls `lsp-listServers` to find "rust-analyzer"
2. Uses `workspace/diagnostic` (future tool) to get all diagnostics
3. Presents a summary of errors and warnings

### 2. Command Execution

**User**: "Expand the macro at cursor in main.rs:42"

**Claude**:
1. Calls `lsp-executeCommand` with:
   ```json
   {
     "serverId": "rust-analyzer",
     "command": "rust-analyzer.expandMacro",
     "arguments": [{"uri": "file:///path/to/main.rs", "position": {"line": 41, "character": 0}}]
   }
   ```
2. Displays the macro expansion

### 3. Code Navigation

**User**: "Find all implementations of the UserService interface"

**Claude**:
1. Calls `lsp/getWorkspaceSymbols` (future tool) to find "UserService"
2. Calls `textDocument/implementation` for each result
3. Lists all implementing classes

## Architecture

```
Claude / AI Assistant
    ↕ (MCP - stdio/JSON-RPC 2.0)
LSP4IJMCPServer
    ↕ (Java API)
LanguageServiceAccessor → LanguageServerWrapper
    ↕ (LSP JSON-RPC)
Language Server Process (rust-analyzer, typescript-language-server, etc.)
```

**Critical Design Choice**: The MCP server does NOT create a new LSP client. It reuses the existing `LanguageServerWrapper` and `LanguageClientImpl`, preserving all custom logic (e.g., Qute LS data model for Java templates).

## Advanced Configuration

### Using a Wrapper Script

Create a script `~/.claude/mcp-servers/lsp4ij-server.sh`:

```bash
#!/bin/bash
# Launch IntelliJ with MCP server for the given project
PROJECT_PATH="$1"
/Applications/IntelliJ\ IDEA.app/Contents/MacOS/idea mcp-server --project "$PROJECT_PATH"
```

Then in `.claude/settings.json`:
```json
{
  "mcpServers": {
    "lsp4ij": {
      "command": "~/.claude/mcp-servers/lsp4ij-server.sh",
      "args": ["${workspaceFolder}"]
    }
  }
}
```

### Debugging

MCP server logs are written to:
- IntelliJ IDEA log: `Help → Show Log in Finder/Explorer`
- Look for lines containing `LSP4IJMCPServer`

**Common issues**:
- **Server not starting**: Check IntelliJ logs for errors
- **Claude can't connect**: Verify the `command` path in `.claude/settings.json`
- **Tools not working**: Ensure language servers are started in IntelliJ (open a relevant file)

## Troubleshooting

### "Language server not found"

**Cause**: The language server hasn't been started yet in IntelliJ.

**Solution**: Open a file of the relevant type (e.g., `.rs` for Rust Analyzer) to trigger server startup, then retry.

### "Command not supported"

**Cause**: The language server doesn't support the requested command.

**Solution**: Call `lsp-listServers` to see available commands for each server.

### MCP client disconnects immediately

**Cause**: The stdio transport setup is incorrect.

**Solution**: 
1. Check that the `command` in `.claude/settings.json` is correct
2. Test the command manually: `idea mcp-server --project /path/to/project`
3. Check IntelliJ logs for startup errors

## Future Tools (Roadmap)

The following tools are planned:

- `lsp/getDocumentSymbols` - Get symbols for a specific document
- `lsp/getWorkspaceSymbols` - Search for symbols in the workspace
- `lsp/getCodeActions` - Get available code actions for a range
- `lsp/getDiagnostics` - Get diagnostics for a file
- `lsp/applyWorkspaceEdit` - Apply edits to files
- `lsp/hover` - Get hover information at a position

## Extension API (For Plugin Developers)

Plugin developers can register custom MCP tools for their language servers using the `MCPToolProvider` extension point (coming soon).

Example:
```java
public class RustMCPToolProvider implements MCPToolProvider {
    @Override
    public String getLanguageServerId() {
        return "rust-analyzer";
    }
    
    @Override
    public void registerTools(ToolRegistry registry, LanguageServiceAccessor accessor) {
        registry.register(new CargoCheckTool(accessor));
        registry.register(new ExpandMacroTool(accessor));
    }
}
```

Register in `plugin.xml`:
```xml
<extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
    <mcpToolProvider implementation="com.example.RustMCPToolProvider"/>
</extensions>
```

## References

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-11-25)
- [LSP4IJ Documentation](./DeveloperGuide.md)
- [Claude Code Documentation](https://claude.ai/code)
- [MCP Design Document](./MCP_DESIGN.md)

## Feedback

Found a bug or have a feature request? Open an issue on [GitHub](https://github.com/redhat-developer/lsp4ij/issues).

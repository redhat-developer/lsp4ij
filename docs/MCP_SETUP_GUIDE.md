# LSP4IJ MCP Server - Setup Guide

## Quick Start (MVP)

The MVP uses a simple manual approach: start the MCP server from IntelliJ, then connect with Claude.

### Step 1: Open Project in IntelliJ

```bash
# Open your project normally in IntelliJ IDEA
# For example: Rust, TypeScript, Java, etc.
```

### Step 2: Start MCP Server

In IntelliJ:
```
Menu → Tools → Start MCP Server
```

**What happens**:
- Undertow HTTP server starts on `localhost:9339`
- SSE endpoint available at `/sse`
- Message endpoint available at `/mcp/message`
- Already-running language servers are exposed as MCP tools
- Notification confirms startup

### Step 3: Configure Claude Code

Create or update `.claude/settings.json` in your project:

```json
{
  "mcpServers": {
    "lsp4ij": {
      "transport": {
        "type": "sse",
        "url": "http://localhost:9339/sse"
      }
    }
  }
}
```

**Note**: The MCP server must be started from IntelliJ first.  
Claude Code will automatically connect to the HTTP endpoint.

### Step 4: Test the MCP Server

Once started, you can interact via MCP tools:

#### Tool 1: `lsp-listServers`

Lists all active language servers in the project.

**Example response**:
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
          "rust-analyzer.viewCrateGraph",
          "rust-analyzer.memoryUsage"
        ]
      }
    }
  ],
  "count": 1
}
```

#### Tool 2: `lsp-executeCommand`

Executes a command on a language server.

**Example request**:
```json
{
  "serverId": "rust-analyzer",
  "command": "rust-analyzer.expandMacro",
  "arguments": [
    {
      "uri": "file:///path/to/main.rs",
      "position": {"line": 42, "character": 5}
    }
  ]
}
```

**Example response**:
```json
{
  "success": true,
  "result": {
    "expansion": "println!(\"Hello, world!\");"
  }
}
```

## Testing the Connection

Test the SSE endpoint directly:

```bash
curl -H "Accept: text/event-stream" http://localhost:9339/sse
```

You should see:
```
event: connected
data: {"status":"connected","server":"lsp4ij-mcp"}
```

### Option B: IntelliJ CLI

*Not implemented in MVP - requires IntelliJ support*

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

## Real-World Use Cases

### Case 1: Rust Development with Claude

1. **Open Rust project in IntelliJ**
2. **Start MCP**: `Tools → Start MCP Server`
3. **In Claude**:
   ```
   You: "What language servers are active?"
   Claude: [calls lsp-listServers]
   → rust-analyzer is active with 14 available commands
   
   You: "Expand the `println!` macro at line 42 of main.rs"
   Claude: [calls lsp-executeCommand]
   → Here's the expansion: println!("Hello, world!");
   ```

### Case 2: Multi-Language Project

1. **Project with Rust + TypeScript**
2. **IntelliJ has started**:
   - rust-analyzer
   - typescript-language-server
3. **Claude can**:
   - List both servers
   - Execute commands on each
   - Get diagnostics from all LS

### Case 3: Debugging with Claude

```
You: "Check all issues in my Rust code"

Claude:
1. [calls lsp-listServers] → finds rust-analyzer
2. [calls lsp/getDiagnostics] (future) → retrieves errors
3. Presents summary: "2 warnings, 1 error"
```

## Troubleshooting

### Problem: "Language server not found"

**Cause**: Language server not started in IntelliJ.

**Solution**:
1. Open a file of the relevant language (e.g., `.rs` for Rust)
2. Wait for LS to start (icon in status bar)
3. Check via `Tools → LSP Console`

### Problem: "Server not started"

**Cause**: MCP server not launched.

**Solution**: `Tools → Start MCP Server`

### Problem: Logs / Debugging

**Where to find logs**:
- IntelliJ: `Help → Show Log in Finder/Explorer`
- Search for: `LSP4IJMCPServer`

**Enable debug logging**:
```
Help → Diagnostic Tools → Debug Log Settings
Add: com.redhat.devtools.lsp4ij.mcp
```

## MVP Features

✅ **HTTP/SSE transport**: Network-based, multi-client capable  
✅ **Manual startup**: Start from IntelliJ Tools menu  
✅ **2 core tools**: `lsp-listServers` + `lsp-executeCommand`  
✅ **Undertow server**: Lightweight embedded servlet container  

## MVP Limitations

1. **Manual startup**: No automatic launch by Claude (requires IntelliJ to be running)
2. **2 tools only**: Additional LSP tools planned for Phase 2

## Roadmap

### Phase 2 (1-2 months)
- [ ] 5+ generic LSP tools
- [ ] Socket transport (HTTP/SSE)
- [ ] Auto-discovery of servers

### Phase 3 (3-4 months)
- [ ] Extension point for custom tools
- [ ] Multi-client support
- [ ] IntelliJ CLI integration

## Feedback

Issues or suggestions?  
→ [GitHub Issues](https://github.com/redhat-developer/lsp4ij/issues)

---

**MVP Ready** ✅  
Next step: Manual testing with Rust Analyzer + Claude Code

# LSP4IJ MCP Server - Known Issues

## ✅ RESOLVED: HTTP/SSE Transport Implemented

### Previous Issue (Stdio Transport)

The initial MVP used **stdio transport**, which only works when the MCP server is launched as a child process.

This was incompatible with our architecture where IntelliJ runs the MCP server and Claude connects as a client.

### ✅ Current Solution: HTTP/SSE with Undertow

**Implemented in version 1.0.0** - The MCP server now uses HTTP/SSE transport:

- **Transport**: HTTP Server-Sent Events (SSE)
- **Server**: Undertow embedded servlet container
- **Endpoints**:
  - `/sse` - SSE endpoint for server-to-client events
  - `/mcp/message` - POST endpoint for client-to-server messages
- **Port**: 9339 (configurable)

### Why This Happens

```
┌─────────────┐     ┌──────────────┐
│  IntelliJ   │     │  Claude Code │
│  (Process A)│     │  (Process B) │
├─────────────┤     ├──────────────┤
│ MCP Server  │  ✗  │ MCP Client   │
│ stdio: in/out     │ stdin/stdout │
│ (tied to IJ)│     │ (separate)   │
└─────────────┘     └──────────────┘
        ↑                    ↑
        └─── No connection ──┘
```

Stdio is a **point-to-point** connection between parent/child processes. 
IntelliJ and Claude are **siblings**, not parent/child.

## ✅ Solution: HTTP/SSE Transport (Required)

To fix this, the MCP server must use **HTTP Server-Sent Events (SSE)** transport instead of stdio.

### What Needs to Change

1. **Add Spring WebFlux dependency** (for HTTP transport):
   ```kotlin
   // build.gradle.kts
   implementation("io.modelcontextprotocol.sdk:mcp-spring-webflux:2.0.0-M2")
   ```

2. **Change transport in LSP4IJMCPServer.java**:
   ```java
   // Instead of:
   var transport = new StdioServerTransportProvider(mcpJsonMapper);
   
   // Use:
   var transport = new SseServerTransportProvider(mcpJsonMapper, 9339);
   // Or use Spring WebFlux HttpHandler
   ```

3. **Update Claude configuration** (`.claude/settings.json`):
   ```json
   {
     "mcpServers": {
       "lsp4ij": {
         "transport": {
           "type": "sse",
           "url": "http://localhost:9339/mcp"
         }
       }
     }
   }
   ```

### Benefits of HTTP/SSE Transport

✅ **Multi-client**: Multiple Claude instances can connect  
✅ **Persistent**: IntelliJ keeps running, Claude reconnects  
✅ **Standard**: HTTP is firewall-friendly  
✅ **Debuggable**: Can test with curl/Postman  

## 🔧 Temporary Workaround (Not Recommended)

There is **no real workaround** for stdio limitation in this architecture.

The only way stdio would work:
- Claude launches IntelliJ as a child process → **Not practical**
- OR we build a **proxy process** that bridges stdio ↔ socket → **Complex**

## 📋 Implementation Checklist

To implement HTTP/SSE transport:

- [ ] Add `mcp-spring-webflux` dependency
- [ ] Create `SseServerTransportProvider` or use Spring WebFlux
- [ ] Configure HTTP port (default: 9339)
- [ ] Add `/mcp` endpoint for SSE connections
- [ ] Update documentation
- [ ] Test with Claude Code
- [ ] Add port conflict handling
- [ ] Add security (optional: token auth)

## 🎯 Recommended Next Steps

### Option A: Quick Fix (Spring WebFlux - Recommended)

Use the MCP SDK's built-in SSE transport:

```kotlin
// Add dependency
implementation("io.modelcontextprotocol.sdk:mcp-spring-webflux:2.0.0-M2")
```

```java
// In LSP4IJMCPServer.java
import io.modelcontextprotocol.server.transport.SseServerTransportProvider;

// Replace stdio transport
var transport = new SseServerTransportProvider(
    mcpJsonMapper, 
    9339  // Port
);
```

**Complexity**: Low (2-3 hours)  
**Pros**: Uses SDK, minimal code  
**Cons**: Adds Spring dependency  

### Option B: Servlet Transport

Use servlet-based transport (lighter than WebFlux):

```kotlin
implementation("io.modelcontextprotocol.sdk:mcp-spring-webmvc:2.0.0-M2")
```

**Complexity**: Medium (4-6 hours)  
**Pros**: Lighter than WebFlux  
**Cons**: Need servlet container setup  

### Option C: Custom HTTP Server

Build a simple HTTP server using Jetty or built-in Java HttpServer:

**Complexity**: High (8-10 hours)  
**Pros**: No Spring dependency, full control  
**Cons**: More code to maintain  

## 📝 Current Status

- ✅ MCP Server core implementation works
- ✅ Tools registered correctly
- ✅ IntelliJ action works
- ❌ **Transport layer blocks Claude connection**

**Priority**: **HIGH** - This blocks all MCP functionality

## 🚀 After Fixing

Once HTTP/SSE transport is implemented:

1. Start IntelliJ with your Rust project
2. `Tools → Start MCP Server` (now listening on :9339)
3. Open Claude Code in same project directory
4. Claude auto-connects via HTTP
5. `--tools` shows: `lsp-listServers`, `lsp-executeCommand`
6. ✅ **Everything works!**

## ℹ️ Why Wasn't This Caught Earlier?

The MCP specification supports multiple transports:
- **Stdio**: For direct parent/child processes
- **HTTP/SSE**: For networked/separate processes

We initially chose stdio thinking IntelliJ could pipe it to Claude, but:
- Stdio is fundamentally process-bound
- Our architecture needs network transport
- This is a design correction, not a bug

## 📚 References

- [MCP Transport Spec](https://modelcontextprotocol.io/specification/2025-11-25#transports)
- [MCP Java SDK - Spring WebFlux](https://github.com/modelcontextprotocol/java-sdk/tree/main/mcp-spring-webflux)
- [Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)

---

**Created**: 2026-06-04  
**Status**: BLOCKING  
**Priority**: HIGH  
**Effort**: 2-6 hours depending on approach

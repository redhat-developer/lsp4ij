# HTTP/SSE Transport Implementation - TODO

## ✅ Problem Identified

**Stdio transport ne fonctionne pas** car IntelliJ et Claude sont des processus séparés.

## 🎯 Solution: HTTP/SSE Transport

Utiliser `HttpServletSseServerTransportProvider` (déjà dans `mcp-core`!)

## 📋 Steps to Implement

### 1. Dependencies (✅ DONE)
```kotlin
compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
```

### 2. Create HTTP Server Wrapper

Créer `HttpMcpServer.java`:
```java
public class HttpMcpServer {
    private HttpServer server;  // com.sun.net.httpserver.HttpServer
    private final int port = 9339;
    
    public void start(McpSyncServer mcpServer, McpJsonMapper mapper) {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Create SSE handler
        server.createContext("/mcp", exchange -> {
            // Handle SSE connection
            // Bridge between HttpExchange and MCP transport
        });
        
        server.start();
    }
}
```

### 3. Update LSP4IJMCPServer.java

```java
// Instead of StdioServerTransportProvider
// Use HttpServletSseServerTransportProvider

// But need to bridge com.sun.net.httpserver → jakarta.servlet
// OR use embedded Jetty (already in IntelliJ platform)
```

### 4. Alternative: Use IntelliJ's Built-in HTTP Server

IntelliJ a déjà un serveur HTTP intégré !

```java
import com.intellij.ide.browsers.BrowserStarter;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.ide.BuiltInServerManager;

// Get IntelliJ's built-in server port
int port = BuiltInServerManager.getInstance().getPort();

// Register endpoint
// ... add /mcp handler
```

### 5. Configure Claude

`.claude/settings.json`:
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

## 🚀 Quickest Solution

**Use Java's HttpServer + Manual SSE**:

1. Create HTTP endpoint `/mcp`
2. Handle SSE protocol manually:
   ```
   Content-Type: text/event-stream
   Cache-Control: no-cache
   Connection: keep-alive
   
   data: {"jsonrpc":"2.0",...}\n\n
   ```
3. Bridge MCP JSON messages ↔ SSE events

**Estimated time**: 3-4 hours

## 📝 Current State

- ✅ MCP server logic works
- ✅ Tools registered
- ❌ Transport layer needs HTTP/SSE
- Files ready:
  - `SimpleHttpSseTransport.java` (starter code)
  - Servlet API dependency added

## 🎯 Next Actions

1. Implement simple HTTP server with SSE support
2. Bridge MCP server → HTTP/SSE
3. Test with `curl`:
   ```bash
   curl http://localhost:9339/mcp
   ```
4. Configure Claude
5. Test end-to-end

---

**Priority**: CRITICAL  
**Blocking**: All MCP functionality  
**Effort**: 3-4 hours  
**Status**: Dependencies added, implementation needed

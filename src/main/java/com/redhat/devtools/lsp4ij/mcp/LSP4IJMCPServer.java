/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPTool;
import com.redhat.devtools.lsp4ij.mcp.toolProvider.MCPToolBean;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.undertow.Undertow;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import jakarta.servlet.ServletException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP (Model Context Protocol) Server for LSP4IJ.
 *
 * This server exposes language servers managed by LSP4IJ as MCP tools,
 * allowing AI assistants like Claude to interact with already-running
 * language servers without duplicating processes or losing IDE context.
 *
 * Architecture:
 * <pre>
 * Claude (MCP client)
 *   ↕ HTTP/SSE JSON-RPC 2.0
 * LSP4IJMCPServer (HTTP server on :9339)
 *   ↕ Java API
 * LanguageServiceAccessor → LanguageServerWrapper → LanguageServer process
 * </pre>
 *
 * Key features:
 * - No process duplication: Reuses LSP servers already launched by IntelliJ
 * - Preserves context: Uses existing LanguageClientImpl with all custom logic
 * - Hot state: Diagnostics, opened files, and project state already available
 *
 * Example MCP tools provided:
 * - lsp/listServers: List all started language servers
 * - lsp/executeCommand: Execute a command on a specific language server
 */
public class LSP4IJMCPServer implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSP4IJMCPServer.class);

    private static final String SERVER_NAME = "lsp4ij";
    private static final String SERVER_VERSION = "1.0.0";
    private static final int DEFAULT_PORT = 9339;
    private static final String SSE_ENDPOINT = "/sse";
    private static final String MESSAGE_ENDPOINT = "/mcp/message";

    private static final ExtensionPointName<MCPToolBean> EP_NAME =
            ExtensionPointName.create("com.redhat.devtools.lsp4ij.mcpTool");

    private final MCPServerManager serverManager;
    private JsonMapper jackson3Mapper;

    private McpSyncServer mcpServer;
    private Undertow undertowServer;
    private HttpServletSseServerTransportProvider transportProvider;
    private boolean started = false;

    // Client working directory from initialization
    private volatile String clientWorkingDirectory = null;

    public LSP4IJMCPServer(@NotNull MCPServerManager serverManager) {
        this.serverManager = serverManager;
    }

    /**
     * Discovers all MCP tools from registered extensions.
     *
     * @return list of tool beans (name, description, implementation)
     */
    private List<MCPToolBean> discoverTools() {
        List<MCPToolBean> allTools = new ArrayList<>();

        // Get all registered tools via extension point
        for (MCPToolBean toolBean : EP_NAME.getExtensionList()) {
            try {
                allTools.add(toolBean);
                LOGGER.info("Discovered MCP tool: {} - {}", toolBean.getName(), toolBean.getDescription());
            } catch (Exception e) {
                LOGGER.error("Error discovering tool: " + toolBean.getName(), e);
            }
        }

        return allTools;
    }

    /**
     * Start the MCP server with HTTP/SSE transport.
     *
     * The server will listen on http://localhost:9339 with:
     * - /sse for SSE connections
     * - /mcp/message for client messages
     *
     * Uses Undertow as the embedded servlet container.
     *
     * @throws IllegalStateException if server is already started
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("MCP server already started");
        }

        LOGGER.info("Starting LSP4IJ MCP Server (application-wide)");

        try {
            // Create Jackson3 JSON mapper
            this.jackson3Mapper = JsonMapper.builder().build();
            var mcpJsonMapper = new JacksonMcpJsonMapper(jackson3Mapper);

            // Create HTTP/SSE transport provider (this is a Servlet)
            transportProvider = HttpServletSseServerTransportProvider.builder()
                    .jsonMapper(mcpJsonMapper)
                    .messageEndpoint(MESSAGE_ENDPOINT)
                    .sseEndpoint(SSE_ENDPOINT)
                    .build();

            // Discover tools from all registered extensions
            List<MCPToolBean> discoveredTools = discoverTools();
            LOGGER.info("Discovered {} MCP tools from extensions", discoveredTools.size());

            // Build MCP server with capabilities and tools
            // IMPORTANT: The McpServer.sync() call initializes the transport provider
            // It registers the server's message handlers with the transport
            var serverBuilder = McpServer.sync(transportProvider)
                    .serverInfo(SERVER_NAME, SERVER_VERSION)
                    .jsonMapper(mcpJsonMapper)
                    .jsonSchemaValidator(new DefaultJsonSchemaValidator())
                    .capabilities(McpSchema.ServerCapabilities.builder()
                            .tools(true)
                            .build());

            // Register all discovered tools from extension point
            for (MCPToolBean toolBean : discoveredTools) {
                MCPTool tool = toolBean.getInstance();
                if (tool != null) {
                    serverBuilder.toolCall(
                            McpSchema.Tool.builder()
                                    .name(toolBean.getName())
                                    .inputSchema(tool.getInputSchema())
                                    .description(toolBean.getDescription())
                                    .build(),
                            (exchange, request) -> {
                                // Log request with pretty JSON using LSP4IJ Gson
                                String requestLog = MCPJsonUtils.toJsonString(request.arguments() != null ? request.arguments() : Map.of());
                                serverManager.notifyRequest(toolBean.getName(), requestLog);

                                // Resolve project from request arguments
                                Project project = resolveProject(request);
                                if (project == null) {
                                    LOGGER.warn("No project resolved for tool call: {}", toolBean.getName());
                                    return McpSchema.CallToolResult.builder()
                                            .content(List.of(new McpSchema.TextContent(
                                                    "Error: No project context. Please specify 'projectPath' or 'projectName' in arguments."
                                            )))
                                            .isError(true)
                                            .build();
                                }

                                // Execute tool
                                McpSchema.CallToolResult result = tool.execute(project, exchange, request);

                                // Log response - tools already use MCPJsonUtils.toJsonString()
                                String responseLog;
                                if (result.content() != null && !result.content().isEmpty()) {
                                    var firstContent = result.content().get(0);
                                    if (firstContent instanceof McpSchema.TextContent textContent) {
                                        // The text is already formatted JSON from MCPJsonUtils
                                        responseLog = textContent.text();
                                    } else {
                                        responseLog = MCPJsonUtils.toJsonString(result.content());
                                    }
                                } else {
                                    responseLog = "null";
                                }
                                serverManager.notifyResponse(toolBean.getName(), responseLog);

                                return result;
                            }
                    );
                    LOGGER.info("Registered MCP tool: {} - {}", toolBean.getName(), toolBean.getDescription());
                }
            }

            mcpServer = serverBuilder.build();

            // CRITICAL: After building the server, the transport provider is now initialized
            // and ready to handle requests. The server's handlers are registered with the transport.

            // Configure Undertow servlet deployment
            // CRITICAL: Use a singleton instance factory to ensure session persistence
            // The same servlet instance must handle all requests to maintain SSE sessions
            final HttpServletSseServerTransportProvider singletonServlet = transportProvider;

            DeploymentInfo servletBuilder = Servlets.deployment()
                    .setClassLoader(LSP4IJMCPServer.class.getClassLoader())
                    .setContextPath("/")
                    .setDeploymentName("lsp4ij-mcp")
                    .addServlets(
                            Servlets.servlet("mcpServlet", HttpServletSseServerTransportProvider.class,
                                            // Singleton factory - ALWAYS return the same instance
                                            new io.undertow.servlet.api.InstanceFactory<HttpServletSseServerTransportProvider>() {
                                                @Override
                                                public io.undertow.servlet.api.InstanceHandle<HttpServletSseServerTransportProvider> createInstance() throws InstantiationException {
                                                    return new io.undertow.servlet.api.InstanceHandle<HttpServletSseServerTransportProvider>() {
                                                        @Override
                                                        public HttpServletSseServerTransportProvider getInstance() {
                                                            return singletonServlet;
                                                        }

                                                        @Override
                                                        public void release() {
                                                            // Never release - we manage lifecycle
                                                        }
                                                    };
                                                }
                                            })
                                    .addMapping("/*")  // Let the servlet handle all paths
                                    .setAsyncSupported(true) // Required for SSE
                    );

            DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
            manager.deploy();

            // Create and start Undertow server
            undertowServer = Undertow.builder()
                    .addHttpListener(DEFAULT_PORT, "localhost")
                    .setHandler(manager.start())
                    .build();

            undertowServer.start();

            started = true;
            LOGGER.info("LSP4IJ MCP Server started successfully");
            LOGGER.info("  SSE endpoint: http://localhost:{}{}", DEFAULT_PORT, SSE_ENDPOINT);
            LOGGER.info("  Message endpoint: http://localhost:{}{}", DEFAULT_PORT, MESSAGE_ENDPOINT);

        } catch (ServletException e) {
            LOGGER.error("Failed to deploy MCP servlet", e);
            started = false;
            throw new RuntimeException("Failed to start MCP server", e);
        } catch (Exception e) {
            LOGGER.error("Failed to start MCP server", e);
            started = false;
            throw new RuntimeException("Failed to start MCP server", e);
        }
    }


    /**
     * Stop the MCP server.
     */
    public void stop() {
        if (!started) {
            return;
        }

        LOGGER.info("Stopping LSP4IJ MCP Server");
        try {
            if (undertowServer != null) {
                undertowServer.stop();
            }
            if (transportProvider != null) {
                transportProvider.close();
            }
            if (mcpServer != null) {
                mcpServer.close();
            }
            started = false;
            LOGGER.info("LSP4IJ MCP Server stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping MCP server", e);
        }
    }

    @Override
    public void dispose() {
        stop();
    }

    /**
     * Check if the server is running.
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * Resolve the project from the request arguments or client working directory.
     * Resolution order:
     * 1. 'projectPath' in arguments
     * 2. 'projectName' in arguments
     * 3. 'cwd' in arguments (Claude working directory)
     * 4. Client working directory from session
     * 5. First open project (fallback)
     */
    private Project resolveProject(McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = request.arguments();

        // Try to resolve by explicit project path in arguments
        if (arguments != null) {
            Object projectPath = arguments.get("projectPath");
            if (projectPath != null) {
                Project project = findProjectByPath(projectPath.toString());
                if (project != null) {
                    return project;
                }
            }

            // Try to resolve by project name
            Object projectName = arguments.get("projectName");
            if (projectName != null) {
                Project project = findProjectByName(projectName.toString());
                if (project != null) {
                    return project;
                }
            }

            // Try to resolve by cwd (Claude working directory)
            Object cwd = arguments.get("cwd");
            if (cwd != null) {
                Project project = findProjectByPath(cwd.toString());
                if (project != null) {
                    clientWorkingDirectory = cwd.toString(); // Cache for future requests
                    return project;
                }
            }
        }

        // Try client working directory from cache
        if (clientWorkingDirectory != null) {
            Project project = findProjectByPath(clientWorkingDirectory);
            if (project != null) {
                return project;
            }
        }

        // Fallback: return first open project
        return getDefaultProject();
    }

    /**
     * Find project by path (exact match or parent path).
     */
    private Project findProjectByPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            String basePath = project.getBasePath();
            if (basePath != null) {
                // Exact match
                if (basePath.equals(path)) {
                    return project;
                }
                // Path is inside project
                if (path.startsWith(basePath)) {
                    return project;
                }
            }
        }
        return null;
    }

    /**
     * Find project by name.
     */
    private Project findProjectByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            if (project.getName().equals(name)) {
                return project;
            }
        }
        return null;
    }

    /**
     * Get the default project (first open project).
     */
    private Project getDefaultProject() {
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        return projects.length > 0 ? projects[0] : null;
    }
}

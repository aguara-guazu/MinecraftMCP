package com.minecraftmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.tools.CommandTool;
import com.minecraftmcp.mcp.tools.PlayerListTool;
import com.minecraftmcp.mcp.tools.PlayerManagementTool;
import com.minecraftmcp.mcp.tools.ServerLogsTool;
import com.minecraftmcp.mcp.tools.ServerStatusTool;
import com.minecraftmcp.mcp.tools.WorldInfoTool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Jetty imports for HTTP server
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP server implementation for MinecraftMCP
 */
public class MCPServer {
    
    private final MinecraftMCPPlugin plugin;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // HTTP server for HTTP transport
    private Server httpServer;
    private final Map<String, AsyncContext> sseClients = new ConcurrentHashMap<>();
    
    // MCP tools registry
    private final Map<String, MCPTool> tools = new HashMap<>();
    
    // MCP resources registry
    private final Map<String, MCPResource> resources = new HashMap<>();
    
    /**
     * Create a new MCP server
     * 
     * @param plugin the plugin instance
     */
    public MCPServer(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newCachedThreadPool();
        
        // Register tools
        registerTools();
    }
    
    /**
     * Start the MCP server
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            plugin.getLogger().info("Starting MCP server with " + plugin.getPluginConfig().getMcpServerTransport() + " transport");
            
            if ("http".equalsIgnoreCase(plugin.getPluginConfig().getMcpServerTransport())) {
                startHttpTransport();
            } else {
                plugin.getLogger().severe("Unsupported transport: " + plugin.getPluginConfig().getMcpServerTransport() + ". Only HTTP transport is supported.");
                running.set(false);
            }
        } else {
            plugin.getLogger().warning("MCP server is already running");
        }
    }
    
    /**
     * Stop the MCP server
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            plugin.getLogger().info("Stopping MCP server");
            executorService.shutdown();
            
            // Stop HTTP server if running
            if (httpServer != null) {
                try {
                    httpServer.stop();
                    plugin.getLogger().info("HTTP server stopped");
                } catch (Exception e) {
                    plugin.getLogger().severe("Error stopping HTTP server: " + e.getMessage());
                }
            }
        } else {
            plugin.getLogger().warning("MCP server is not running");
        }
    }
    
    /**
     * Check if the MCP server is running
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }
    
    
    /**
     * Send SSE event to a specific client
     * 
     * @param sessionId the session ID of the client
     * @param event the event type
     * @param data the event data
     * @return true if successful, false otherwise
     */
    private boolean sendSseEvent(String sessionId, String event, String data) {
        AsyncContext context = sseClients.get(sessionId);
        if (context == null) {
            return false;
        }
        
        try {
            HttpServletResponse response = (HttpServletResponse) context.getResponse();
            PrintWriter writer = response.getWriter();
            
            if (event != null && !event.isEmpty()) {
                writer.write("event: " + event + "\n");
            }
            writer.write("data: " + data + "\n\n");
            writer.flush();
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Error sending SSE event: " + e.getMessage());
            sseClients.remove(sessionId);
            context.complete();
            return false;
        }
    }
    
    /**
     * Send SSE event to all connected clients
     * 
     * @param event the event type
     * @param data the event data
     */
    private void sendSseEventToAll(String event, String data) {
        for (String sessionId : sseClients.keySet()) {
            sendSseEvent(sessionId, event, data);
        }
    }
    
    /**
     * Process an MCP request
     * 
     * @param request the request string
     * @return the response string
     */
    private String processRequest(String request) {
        try {
            JsonNode requestNode = objectMapper.readTree(request);
            
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("MCP request: " + request);
            }
            
            // Extract request fields
            String method = requestNode.path("method").asText();
            JsonNode params = requestNode.path("params");
            String id = requestNode.has("id") ? requestNode.path("id").asText() : null;
            
            // Process request
            ObjectNode responseNode = JsonNodeFactory.instance.objectNode();
            
            switch (method) {
                case "initialize":
                    // Handle MCP initialization
                    responseNode.set("result", handleInitialize(params));
                    break;
                case "tools/list":
                    // Handle tools list request
                    responseNode.set("result", handleToolsList(params));
                    break;
                case "tools/call":
                    // Handle tool call
                    responseNode.set("result", handleToolsCall(params));
                    break;
                case "resources/list":
                    // Handle resources list request  
                    responseNode.set("result", handleResourcesList(params));
                    break;
                case "resources/read":
                    // Handle resource read request
                    responseNode.set("result", handleResourcesRead(params));
                    break;
                default:
                    // Unsupported method
                    ObjectNode error = JsonNodeFactory.instance.objectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found: " + method);
                    responseNode.set("error", error);
            }
            
            // Add response ID if present in request
            if (id != null) {
                responseNode.put("id", id);
            }
            
            // Add JSON-RPC version
            responseNode.put("jsonrpc", "2.0");
            
            return objectMapper.writeValueAsString(responseNode);
        } catch (Exception e) {
            plugin.getLogger().severe("Error processing MCP request: " + e.getMessage());
            
            try {
                ObjectNode response = JsonNodeFactory.instance.objectNode();
                ObjectNode error = JsonNodeFactory.instance.objectNode();
                error.put("code", -32700);
                error.put("message", "Parse error");
                response.set("error", error);
                response.put("jsonrpc", "2.0");
                
                return objectMapper.writeValueAsString(response);
            } catch (Exception ex) {
                return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
            }
        }
    }
    
    /**
     * Handle MCP initialization
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleInitialize(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract client info according to MCP protocol
            String protocolVersion = params.path("protocolVersion").asText("2024-11-05");
            JsonNode clientInfo = params.path("clientInfo");
            String clientName = clientInfo.path("name").asText("unknown");
            String clientVersion = clientInfo.path("version").asText("unknown");
            
            // MCP initialization response structure
            result.put("protocolVersion", "2024-11-05");
            
            // Create server info according to MCP protocol
            ObjectNode serverInfo = JsonNodeFactory.instance.objectNode();
            serverInfo.put("name", "MinecraftMCP");
            serverInfo.put("version", plugin.getDescription().getVersion());
            result.set("serverInfo", serverInfo);
            
            // Create capabilities according to MCP protocol
            ObjectNode capabilities = JsonNodeFactory.instance.objectNode();
            if (plugin.getPluginConfig().areToolsEnabled()) {
                capabilities.set("tools", JsonNodeFactory.instance.objectNode());
            }
            if (plugin.getPluginConfig().areResourcesEnabled()) {
                capabilities.set("resources", JsonNodeFactory.instance.objectNode());
            }
            if (plugin.getPluginConfig().arePromptsEnabled()) {
                capabilities.set("prompts", JsonNodeFactory.instance.objectNode());
            }
            if (plugin.getPluginConfig().isLoggingEnabled()) {
                capabilities.set("logging", JsonNodeFactory.instance.objectNode());
            }
            result.set("capabilities", capabilities);
            
            // Log connection
            plugin.getLogger().info("MCP client connected: " + clientName + " " + clientVersion);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling initialization: " + e.getMessage());
            // Return error in MCP format
            ObjectNode errorResult = JsonNodeFactory.instance.objectNode();
            errorResult.put("code", -32603);
            errorResult.put("message", "Internal error: " + e.getMessage());
            result.set("error", errorResult);
            return result;
        }
    }
    
    /**
     * Handle tools/list request
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleToolsList(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Create tools array according to MCP protocol
            result.set("tools", objectMapper.valueToTree(getToolDefinitions()));
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error listing tools: " + e.getMessage());
            ObjectNode errorResult = JsonNodeFactory.instance.objectNode();
            errorResult.put("code", -32603);
            errorResult.put("message", "Internal error: " + e.getMessage());
            result.set("error", errorResult);
            return result;
        }
    }
    
    /**
     * Handle tools/call request
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleToolsCall(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract parameters according to MCP protocol
            String toolName = params.path("name").asText();
            JsonNode arguments = params.path("arguments");
            
            // Get tool
            MCPTool tool = tools.get(toolName);
            
            if (tool == null) {
                result.put("status", "error");
                result.put("error", "Tool not found: " + toolName);
                return result;
            }
            
            // Execute tool and format response according to MCP protocol
            ObjectNode toolResult = tool.execute(arguments);
            
            // Format as MCP tool response
            result.set("content", toolResult.get("content"));
            if (toolResult.has("isError") && toolResult.get("isError").asBoolean()) {
                result.put("isError", true);
            }
            
            // Log tool execution
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Executed tool: " + toolName);
            }
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing tool: " + e.getMessage());
            ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
            errorContent.put("type", "text");
            errorContent.put("text", "Tool execution failed: " + e.getMessage());
            result.set("content", objectMapper.valueToTree(new ObjectNode[]{errorContent}));
            result.put("isError", true);
            return result;
        }
    }
    
    /**
     * Handle resources/list request
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleResourcesList(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Create resources array according to MCP protocol
            result.set("resources", objectMapper.valueToTree(getResourceDefinitions()));
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error listing resources: " + e.getMessage());
            ObjectNode errorResult = JsonNodeFactory.instance.objectNode();
            errorResult.put("code", -32603);
            errorResult.put("message", "Internal error: " + e.getMessage());
            result.set("error", errorResult);
            return result;
        }
    }
    
    /**
     * Handle resources/read request
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleResourcesRead(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract parameters according to MCP protocol
            String uri = params.path("uri").asText();
            
            // Extract resource name from URI
            String resourceName = uri.replaceFirst("^resource://", "").split("/")[0];
            
            // Get resource
            MCPResource resource = resources.get(resourceName);
            
            if (resource == null) {
                ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
                errorContent.put("type", "text");
                errorContent.put("text", "Resource not found: " + resourceName);
                result.set("contents", objectMapper.valueToTree(new ObjectNode[]{errorContent}));
                return result;
            }
            
            // Get resource and format according to MCP protocol
            ObjectNode resourceResult = resource.get(uri, params);
            result.set("contents", resourceResult.get("contents"));
            
            // Log resource request
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Fetched resource: " + uri);
            }
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error fetching resource: " + e.getMessage());
            ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
            errorContent.put("type", "text");
            errorContent.put("text", "Resource fetch failed: " + e.getMessage());
            result.set("contents", objectMapper.valueToTree(new ObjectNode[]{errorContent}));
            return result;
        }
    }
    
    /**
     * Start the HTTP transport
     */
    private void startHttpTransport() {
        executorService.submit(() -> {
            try {
                // Create and configure the HTTP server
                int port = plugin.getPluginConfig().getHttpPort();
                String endpoint = plugin.getPluginConfig().getHttpEndpoint();
                
                httpServer = new Server();
                ServerConnector connector = new ServerConnector(httpServer);
                connector.setPort(port);
                httpServer.addConnector(connector);
                
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                httpServer.setHandler(context);
                
                // Add MCP API endpoint servlet for handling MCP requests
                context.addServlet(new ServletHolder(new MCPApiServlet()), endpoint);
                
                // Add SSE endpoint if enabled
                if (plugin.getPluginConfig().isHttpSseEnabled()) {
                    context.addServlet(new ServletHolder(new MCPSseServlet()), endpoint + "/sse");
                }
                
                // Add security filter
                if (plugin.getPluginConfig().isHttpAccessLoggingEnabled()) {
                    plugin.getLogger().info("HTTP access logging enabled");
                }
                
                // Start the server
                httpServer.start();
                plugin.getLogger().info("HTTP server started on port " + port + ", endpoint: " + endpoint);
                plugin.getLogger().info("MCP HTTP transport ready");
                
                // Run the server in a blocking way
                httpServer.join();
            } catch (Exception e) {
                plugin.getLogger().severe("Error starting HTTP transport: " + e.getMessage());
                e.printStackTrace();
                running.set(false);
            }
        });
    }
    
    /**
     * HTTP servlet for handling MCP API requests
     */
    private class MCPApiServlet extends HttpServlet {
        @Override
        protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            // Add CORS headers if enabled (for preflight requests)
            if (plugin.getPluginConfig().isHttpCorsEnabled()) {
                String allowedOrigins = plugin.getPluginConfig().getAllowedCorsOrigins();
                response.setHeader("Access-Control-Allow-Origin", allowedOrigins);
                response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "X-API-Key, Content-Type");
                response.setStatus(HttpServletResponse.SC_OK);
            }
        }
        
        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            // Add CORS headers if enabled
            if (plugin.getPluginConfig().isHttpCorsEnabled()) {
                String allowedOrigins = plugin.getPluginConfig().getAllowedCorsOrigins();
                response.setHeader("Access-Control-Allow-Origin", allowedOrigins);
                response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "X-API-Key, Content-Type");
            }
            
            // Access logging
            if (plugin.getPluginConfig().isHttpAccessLoggingEnabled()) {
                plugin.getLogger().info("HTTP API request from " + request.getRemoteAddr());
            }
            
            // Validate authentication if enabled
            if (plugin.getPluginConfig().isApiKeyAuthEnabled()) {
                String apiKey = request.getHeader("X-API-Key");
                String source = request.getRemoteAddr();
                
                if (apiKey == null || !plugin.getSecurityManager().validateApiKey(apiKey, "MCP-HTTP-" + source)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().println("{\"error\":\"Authentication failed\"}");
                    return;
                }
                
                // Check if localhost only is enforced
                if (plugin.getPluginConfig().isLocalhostOnly() && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().println("{\"error\":\"Localhost only connections are enforced\"}");
                    return;
                }
            }
            
            // Read the request body
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            // Process the MCP request
            String requestBody = sb.toString();
            String responseBody = processRequest(requestBody);
            
            // Write the response
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(responseBody);
        }
    }
    
    /**
     * HTTP servlet for handling SSE connections
     */
    private class MCPSseServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            // Validate authentication if enabled
            if (plugin.getPluginConfig().isApiKeyAuthEnabled()) {
                String apiKey = request.getHeader("X-API-Key");
                String source = request.getRemoteAddr();
                
                if (apiKey == null || !plugin.getSecurityManager().validateApiKey(apiKey, "MCP-SSE-" + source)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().println("{\"error\":\"Authentication failed\"}");
                    return;
                }
                
                // Check if localhost only is enforced
                if (plugin.getPluginConfig().isLocalhostOnly() && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1")) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().println("{\"error\":\"Localhost only connections are enforced\"}");
                    return;
                }
            }
            
            // Check if we've reached the maximum number of connections
            int maxConnections = plugin.getPluginConfig().getMaxHttpConnections();
            if (sseClients.size() >= maxConnections) {
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.getWriter().println("{\"error\":\"Maximum number of connections reached\"}");
                return;
            }
            
            // Add CORS headers if enabled
            if (plugin.getPluginConfig().isHttpCorsEnabled()) {
                String allowedOrigins = plugin.getPluginConfig().getAllowedCorsOrigins();
                response.setHeader("Access-Control-Allow-Origin", allowedOrigins);
                response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "X-API-Key");
                response.setHeader("Access-Control-Expose-Headers", "X-Session-ID");
                
                // Handle preflight requests
                if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
            }
            
            // Generate session ID for this SSE connection
            UUID sessionId = UUID.randomUUID();
            
            // Configure the response for SSE
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("X-Session-ID", sessionId.toString());
            
            // Create async context and store it
            AsyncContext asyncContext = request.startAsync();
            asyncContext.setTimeout(0); // No timeout
            sseClients.put(sessionId.toString(), asyncContext);
            
            // Add listeners for client disconnection using Jakarta Servlet 5.0 API
            asyncContext.addListener(new jakarta.servlet.AsyncListener() {
                @Override
                public void onTimeout(AsyncEvent event) {
                    sseClients.remove(sessionId.toString());
                    plugin.getLogger().info("SSE client timed out: " + sessionId);
                }
                
                @Override
                public void onError(AsyncEvent event) {
                    sseClients.remove(sessionId.toString());
                    plugin.getLogger().info("SSE client error: " + sessionId);
                }
                
                @Override
                public void onComplete(AsyncEvent event) {
                    sseClients.remove(sessionId.toString());
                    plugin.getLogger().info("SSE client disconnected: " + sessionId);
                }
                
                @Override
                public void onStartAsync(AsyncEvent event) {
                    // Not used
                }
            });
            
            // Send a welcome message
            try {
                PrintWriter writer = response.getWriter();
                writer.write("data: {\"type\":\"connected\",\"sessionId\":\"" + sessionId + "\"}\n\n");
                writer.flush();
            } catch (IOException e) {
                plugin.getLogger().severe("Error sending SSE welcome message: " + e.getMessage());
                asyncContext.complete();
                sseClients.remove(sessionId.toString());
            }
            
            // Log the connection
            plugin.getLogger().info("SSE client connected: " + sessionId);
        }
    }
    
    /**
     * Register MCP tools
     */
    private void registerTools() {
        // Server tools
        tools.put("minecraft_execute_command", new CommandTool(plugin));
        tools.put("minecraft_server_status", new ServerStatusTool(plugin));
        tools.put("minecraft_server_logs", new ServerLogsTool(plugin));
        
        // Player tools
        tools.put("minecraft_player_list", new PlayerListTool(plugin));
        tools.put("minecraft_manage_player", new PlayerManagementTool(plugin));
        
        // World tools
        tools.put("minecraft_world_info", new WorldInfoTool(plugin));
        
        plugin.getLogger().info("Registered " + tools.size() + " MCP tools");
    }
    
    /**
     * Get tool definitions for MCP protocol
     * 
     * @return array of tool definitions
     */
    private ObjectNode[] getToolDefinitions() {
        return tools.entrySet().stream()
            .map(entry -> {
                ObjectNode toolDef = JsonNodeFactory.instance.objectNode();
                toolDef.put("name", entry.getKey());
                toolDef.put("description", entry.getValue().getDescription());
                toolDef.set("inputSchema", entry.getValue().getInputSchema());
                return toolDef;
            })
            .toArray(ObjectNode[]::new);
    }
    
    /**
     * Get resource definitions for MCP protocol
     * 
     * @return array of resource definitions
     */
    private ObjectNode[] getResourceDefinitions() {
        return resources.entrySet().stream()
            .map(entry -> {
                ObjectNode resourceDef = JsonNodeFactory.instance.objectNode();
                resourceDef.put("uri", "resource://" + entry.getKey());
                resourceDef.put("name", entry.getValue().getName());
                resourceDef.put("description", entry.getValue().getDescription());
                resourceDef.put("mimeType", entry.getValue().getMimeType());
                return resourceDef;
            })
            .toArray(ObjectNode[]::new);
    }
}
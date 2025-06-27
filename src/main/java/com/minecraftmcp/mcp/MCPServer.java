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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP server implementation for MinecraftMCP
 */
public class MCPServer {
    
    private final MinecraftMCPPlugin plugin;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
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
            
            if ("stdio".equalsIgnoreCase(plugin.getPluginConfig().getMcpServerTransport())) {
                startStdioTransport();
            } else {
                plugin.getLogger().severe("Unsupported transport: " + plugin.getPluginConfig().getMcpServerTransport());
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
     * Start the STDIO transport
     */
    private void startStdioTransport() {
        executorService.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter writer = new PrintWriter(System.out, true);
            
            plugin.getLogger().info("MCP STDIO transport started");
            
            while (running.get()) {
                try {
                    String line = reader.readLine();
                    
                    if (line == null) {
                        // End of stream, exit loop
                        break;
                    }
                    
                    // Process MCP request
                    String response = processRequest(line);
                    
                    // Write response
                    writer.println(response);
                    writer.flush();
                } catch (IOException e) {
                    plugin.getLogger().severe("Error reading from stdin: " + e.getMessage());
                    break;
                }
            }
            
            plugin.getLogger().info("MCP STDIO transport stopped");
        });
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
                case "mcp.initialize":
                    // Handle session initialization
                    responseNode.set("result", handleInitialize(params));
                    break;
                case "mcp.call_tool":
                    // Handle tool call
                    responseNode.set("result", handleCallTool(params));
                    break;
                case "mcp.get_resource":
                    // Handle resource request
                    responseNode.set("result", handleGetResource(params));
                    break;
                default:
                    // Unsupported method
                    ObjectNode error = JsonNodeFactory.instance.objectNode();
                    error.put("code", -32601);
                    error.put("message", "Method not found");
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
     * Handle session initialization
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleInitialize(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract client info
            String clientId = params.path("client_id").asText();
            String clientName = params.path("client_name").asText();
            String clientVersion = params.path("client_version").asText();
            
            // Validate authentication if enabled
            if (plugin.getPluginConfig().isApiKeyAuthEnabled()) {
                String apiKey = params.path("auth").path("api_key").asText();
                String source = "MCP-" + clientId;
                
                boolean isAuthenticated = plugin.getSecurityManager().validateApiKey(apiKey, source);
                
                if (!isAuthenticated) {
                    result.put("status", "error");
                    result.put("error", "Authentication failed");
                    return result;
                }
                
                // Create session
                UUID sessionId = plugin.getSecurityManager().createSession(source);
                
                result.put("session_id", sessionId.toString());
            }
            
            // Create server info
            ObjectNode serverInfo = JsonNodeFactory.instance.objectNode();
            serverInfo.put("name", "MinecraftMCP");
            serverInfo.put("version", plugin.getDescription().getVersion());
            result.set("server_info", serverInfo);
            
            // Create capabilities
            ObjectNode capabilities = JsonNodeFactory.instance.objectNode();
            capabilities.put("tools", plugin.getPluginConfig().areToolsEnabled());
            capabilities.put("resources", plugin.getPluginConfig().areResourcesEnabled());
            capabilities.put("prompts", plugin.getPluginConfig().arePromptsEnabled());
            capabilities.put("logging", plugin.getPluginConfig().isLoggingEnabled());
            result.set("capabilities", capabilities);
            
            result.put("status", "ok");
            
            // Log connection
            plugin.getLogger().info("MCP client connected: " + clientName + " " + clientVersion + " (" + clientId + ")");
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error handling initialization: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Internal server error: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Handle tool call
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleCallTool(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract parameters
            String sessionId = params.path("session_id").asText();
            String toolName = params.path("name").asText();
            JsonNode arguments = params.path("arguments");
            
            // Validate session
            if (plugin.getPluginConfig().isApiKeyAuthEnabled()) {
                try {
                    boolean isValid = plugin.getSecurityManager().validateSession(UUID.fromString(sessionId));
                    
                    if (!isValid) {
                        result.put("status", "error");
                        result.put("error", "Invalid or expired session");
                        return result;
                    }
                } catch (IllegalArgumentException e) {
                    result.put("status", "error");
                    result.put("error", "Invalid session ID format");
                    return result;
                }
            }
            
            // Get tool
            MCPTool tool = tools.get(toolName);
            
            if (tool == null) {
                result.put("status", "error");
                result.put("error", "Tool not found: " + toolName);
                return result;
            }
            
            // Execute tool
            result = tool.execute(arguments);
            
            // Log tool execution
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Executed tool: " + toolName);
            }
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing tool: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Tool execution failed: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Handle resource request
     * 
     * @param params the request parameters
     * @return the response
     */
    private ObjectNode handleGetResource(JsonNode params) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Extract parameters
            String sessionId = params.path("session_id").asText();
            String uri = params.path("uri").asText();
            JsonNode parameters = params.path("parameters");
            
            // Validate session
            if (plugin.getPluginConfig().isApiKeyAuthEnabled()) {
                try {
                    boolean isValid = plugin.getSecurityManager().validateSession(UUID.fromString(sessionId));
                    
                    if (!isValid) {
                        result.put("status", "error");
                        result.put("error", "Invalid or expired session");
                        return result;
                    }
                } catch (IllegalArgumentException e) {
                    result.put("status", "error");
                    result.put("error", "Invalid session ID format");
                    return result;
                }
            }
            
            // TODO: Extract resource name from URI
            String resourceName = uri.replaceFirst("^resource://", "").split("/")[0];
            
            // Get resource
            MCPResource resource = resources.get(resourceName);
            
            if (resource == null) {
                result.put("status", "error");
                result.put("error", "Resource not found: " + resourceName);
                return result;
            }
            
            // Get resource
            result = resource.get(uri, parameters);
            
            // Log resource request
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Fetched resource: " + uri);
            }
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error fetching resource: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Resource fetch failed: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Register MCP tools
     */
    private void registerTools() {
        // Server tools
        tools.put("execute_command", new CommandTool(plugin));
        tools.put("get_server_status", new ServerStatusTool(plugin));
        tools.put("get_server_logs", new ServerLogsTool(plugin));
        
        // Player tools
        tools.put("get_player_list", new PlayerListTool(plugin));
        tools.put("manage_player", new PlayerManagementTool(plugin));
        
        // World tools
        tools.put("get_world_info", new WorldInfoTool(plugin));
        
        plugin.getLogger().info("Registered " + tools.size() + " MCP tools");
    }
}
package com.minecraftmcp.config;

import com.minecraftmcp.MinecraftMCPPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Configuration management for MinecraftMCP
 */
public class MCPConfig {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new configuration manager
     * 
     * @param plugin the plugin instance
     */
    public MCPConfig(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    /* MCP Server Configuration */
    
    /**
     * Check if the MCP server is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isMcpServerEnabled() {
        return plugin.getConfig().getBoolean("mcp-server.enabled", true);
    }
    
    /**
     * Get the MCP server transport type (http only)
     * 
     * @return the transport type
     */
    public String getMcpServerTransport() {
        return plugin.getConfig().getString("mcp-server.transport", "http");
    }
    
    
    /**
     * Get the HTTP port for the MCP server
     * 
     * @return the HTTP port
     */
    public int getHttpPort() {
        return plugin.getConfig().getInt("mcp-server.http.port", 25575);
    }
    
    /**
     * Get the HTTP endpoint for the MCP server
     * 
     * @return the HTTP endpoint
     */
    public String getHttpEndpoint() {
        return plugin.getConfig().getString("mcp-server.http.endpoint", "/mcp");
    }
    
    /**
     * Check if SSE is enabled for the HTTP transport
     * 
     * @return true if SSE is enabled, false otherwise
     */
    public boolean isHttpSseEnabled() {
        return plugin.getConfig().getBoolean("mcp-server.http.sse-enabled", true);
    }
    
    /**
     * Get the maximum number of SSE connections allowed
     * 
     * @return the maximum number of SSE connections
     */
    public int getMaxHttpConnections() {
        return plugin.getConfig().getInt("mcp-server.http.max-connections", 5);
    }
    
    /**
     * Check if CORS is enabled for HTTP transport
     * 
     * @return true if CORS is enabled, false otherwise
     */
    public boolean isHttpCorsEnabled() {
        return plugin.getConfig().getBoolean("mcp-server.http.cors.enabled", false);
    }
    
    /**
     * Get the allowed origins for CORS
     * 
     * @return the allowed origins
     */
    public String getAllowedCorsOrigins() {
        return plugin.getConfig().getString("mcp-server.http.cors.allowed-origins", "*");
    }
    
    /**
     * Check if HTTP access logging is enabled
     * 
     * @return true if access logging is enabled, false otherwise
     */
    public boolean isHttpAccessLoggingEnabled() {
        return plugin.getConfig().getBoolean("mcp-server.http.access-logging", false);
    }
    
    /* Security Configuration */
    
    /**
     * Check if API key authentication is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isApiKeyAuthEnabled() {
        return plugin.getConfig().getBoolean("security.authentication.api-key-enabled", true);
    }
    
    /**
     * Get the API key for MCP authentication
     * 
     * @return the API key
     */
    public String getApiKey() {
        return plugin.getConfig().getString("security.authentication.api-key", "change-this-to-a-secure-value");
    }
    
    /**
     * Check if localhost-only connections are enforced
     * 
     * @return true if localhost-only, false otherwise
     */
    public boolean isLocalhostOnly() {
        return plugin.getConfig().getBoolean("security.authentication.localhost-only", true);
    }
    
    /**
     * Get the session timeout in minutes
     * 
     * @return the session timeout
     */
    public int getSessionTimeout() {
        return plugin.getConfig().getInt("security.authentication.session-timeout", 30);
    }
    
    /**
     * Check if command whitelisting is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isCommandWhitelistEnabled() {
        return plugin.getConfig().getBoolean("security.command-whitelist.enabled", true);
    }
    
    /**
     * Get the list of allowed commands
     * 
     * @return the list of allowed commands
     */
    public List<String> getAllowedCommands() {
        return plugin.getConfig().getStringList("security.command-whitelist.allowed-commands");
    }
    
    /**
     * Check if rate limiting is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isRateLimitingEnabled() {
        return plugin.getConfig().getBoolean("security.rate-limiting.enabled", true);
    }
    
    /**
     * Get the maximum number of commands per minute
     * 
     * @return the max commands per minute
     */
    public int getMaxCommandsPerMinute() {
        return plugin.getConfig().getInt("security.rate-limiting.commands-per-minute", 30);
    }
    
    /**
     * Get the maximum number of failed authentication attempts
     * 
     * @return the max auth attempts
     */
    public int getMaxAuthAttempts() {
        return plugin.getConfig().getInt("security.rate-limiting.max-auth-attempts", 5);
    }
    
    /**
     * Get the temporary ban duration in minutes
     * 
     * @return the temp ban duration
     */
    public int getTempBanDuration() {
        return plugin.getConfig().getInt("security.rate-limiting.temp-ban-duration", 15);
    }
    
    /* Debug Configuration */
    
    /**
     * Check if debug mode is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("debug.enabled", false);
    }
    
    /**
     * Get the log level
     * 
     * @return the log level
     */
    public String getLogLevel() {
        return plugin.getConfig().getString("debug.log-level", "INFO");
    }
    
    /* Features Configuration */
    
    /**
     * Check if player tracking is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isPlayerTrackingEnabled() {
        return plugin.getConfig().getBoolean("features.player-tracking", true);
    }
    
    /**
     * Check if world manipulation is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isWorldManipulationEnabled() {
        return plugin.getConfig().getBoolean("features.world-manipulation", true);
    }
    
    /**
     * Check if inventory manipulation is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isInventoryManipulationEnabled() {
        return plugin.getConfig().getBoolean("features.inventory-manipulation", true);
    }
    
    /* Capabilities Configuration */
    
    /**
     * Check if MCP tools are enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean areToolsEnabled() {
        return plugin.getConfig().getBoolean("capabilities.tools", true);
    }
    
    /**
     * Check if MCP resources are enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean areResourcesEnabled() {
        return plugin.getConfig().getBoolean("capabilities.resources", true);
    }
    
    /**
     * Check if MCP prompts are enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean arePromptsEnabled() {
        return plugin.getConfig().getBoolean("capabilities.prompts", true);
    }
    
    /**
     * Check if MCP logging is enabled
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isLoggingEnabled() {
        return plugin.getConfig().getBoolean("capabilities.logging", true);
    }
}
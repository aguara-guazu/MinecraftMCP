package com.minecraftmcp;

import com.minecraftmcp.commands.MCPCommand;
import com.minecraftmcp.config.MCPConfig;
import com.minecraftmcp.listeners.PlayerEventListener;
import com.minecraftmcp.mcp.MCPServer;
import com.minecraftmcp.security.SecurityManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for MinecraftMCP
 */
public class MinecraftMCPPlugin extends JavaPlugin {
    
    private MCPConfig config;
    private MCPServer mcpServer;
    private SecurityManager securityManager;
    
    @Override
    public void onEnable() {
        // Load configuration
        saveDefaultConfig();
        this.config = new MCPConfig(this);
        
        // Initialize security manager
        this.securityManager = new SecurityManager(this);
        
        // Register commands
        getCommand("mcp").setExecutor(new MCPCommand(this));
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        
        // Start MCP server if enabled
        if (config.isMcpServerEnabled()) {
            this.mcpServer = new MCPServer(this);
            mcpServer.start();
            getLogger().info("MCP Server started with " + config.getMcpServerTransport() + " transport");
        }
        
        getLogger().info("MinecraftMCP plugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // Shutdown MCP server if running
        if (mcpServer != null) {
            mcpServer.stop();
            getLogger().info("MCP Server stopped");
        }
        
        getLogger().info("MinecraftMCP plugin has been disabled!");
    }
    
    /**
     * Get the plugin configuration
     * 
     * @return the configuration manager
     */
    public MCPConfig getPluginConfig() {
        return config;
    }
    
    /**
     * Get the MCP server instance
     * 
     * @return the MCP server
     */
    public MCPServer getMcpServer() {
        return mcpServer;
    }
    
    /**
     * Get the security manager
     * 
     * @return the security manager
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
}
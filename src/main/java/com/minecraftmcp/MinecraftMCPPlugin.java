package com.minecraftmcp;

import com.minecraftmcp.commands.MCPCommand;
import com.minecraftmcp.config.MCPConfig;
import com.minecraftmcp.listeners.PlayerEventListener;
import com.minecraftmcp.mcp.MCPServer;
import com.minecraftmcp.security.SecurityManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

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
        
        // Register commands using Paper's approach
        MCPCommand mcpCommand = new MCPCommand(this);
        
        // Create and register the command directly with the command map
        // Paper plugins don't use plugin.yml for commands
        Command mcpPluginCommand = new Command("mcp") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return mcpCommand.onCommand(sender, this, commandLabel, args);
            }
            
            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return mcpCommand.onTabComplete(sender, this, alias, args);
            }
        };
        
        // Set command details
        mcpPluginCommand.setDescription("MCP commands for Minecraft integration");
        mcpPluginCommand.setUsage("/mcp <subcommand> [args]");
        mcpPluginCommand.setPermission("minecraftmcp.commands.mcp");
        mcpPluginCommand.setAliases(Arrays.asList("minecraftmcp"));
        
        // Register the command
        getServer().getCommandMap().register("minecraftmcp", mcpPluginCommand);
        
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
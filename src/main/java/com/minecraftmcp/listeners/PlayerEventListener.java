package com.minecraftmcp.listeners;

import com.minecraftmcp.MinecraftMCPPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for player events
 */
public class PlayerEventListener implements Listener {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new player event listener
     * 
     * @param plugin the plugin instance
     */
    public PlayerEventListener(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle player join events
     * 
     * @param event the player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Example of tracking player joins if feature is enabled
        if (plugin.getPluginConfig().isPlayerTrackingEnabled()) {
            plugin.getLogger().info("Player joined: " + event.getPlayer().getName());
            
            // Here you could implement any additional MCP-related functionality
            // For example, sending player join events to connected MCP clients
            if (plugin.getMcpServer() != null && plugin.getMcpServer().isRunning()) {
                // Notify MCP clients of player join
            }
        }
    }
    
    /**
     * Handle player quit events
     * 
     * @param event the player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Example of tracking player quits if feature is enabled
        if (plugin.getPluginConfig().isPlayerTrackingEnabled()) {
            plugin.getLogger().info("Player quit: " + event.getPlayer().getName());
            
            // Here you could implement any additional MCP-related functionality
            // For example, sending player quit events to connected MCP clients
            if (plugin.getMcpServer() != null && plugin.getMcpServer().isRunning()) {
                // Notify MCP clients of player quit
            }
        }
    }
}
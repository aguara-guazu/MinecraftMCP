package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;
import org.bukkit.Bukkit;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * MCP tool for getting server status
 */
public class ServerStatusTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new server status tool
     * 
     * @param plugin the plugin instance
     */
    public ServerStatusTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "get_server_status";
    }
    
    @Override
    public String getDescription() {
        return "Get the status of the Minecraft server";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        schema.set("properties", JsonNodeFactory.instance.objectNode());
        schema.set("required", JsonNodeFactory.instance.arrayNode());
        return schema;
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Basic server status
            ObjectNode serverStatus = JsonNodeFactory.instance.objectNode();
            serverStatus.put("version", Bukkit.getVersion());
            serverStatus.put("bukkitVersion", Bukkit.getBukkitVersion());
            serverStatus.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
            serverStatus.put("maxPlayers", Bukkit.getMaxPlayers());
            
            // TPS (ticks per second) - Paper API specific
            try {
                double[] tps = Bukkit.getTPS();
                ObjectNode tpsNode = JsonNodeFactory.instance.objectNode();
                tpsNode.put("1m", tps[0]);
                tpsNode.put("5m", tps[1]);
                tpsNode.put("15m", tps[2]);
                serverStatus.set("tps", tpsNode);
                
                // MSPT - Paper API specific
                serverStatus.put("mspt", Bukkit.getAverageTickTime());
            } catch (Exception e) {
                // Silently ignore if not available (non-Paper server)
            }
            
            // Memory usage
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
            
            ObjectNode memoryStatus = JsonNodeFactory.instance.objectNode();
            memoryStatus.put("max", heapMemoryUsage.getMax() / (1024 * 1024) + " MB");
            memoryStatus.put("used", heapMemoryUsage.getUsed() / (1024 * 1024) + " MB");
            memoryStatus.put("committed", heapMemoryUsage.getCommitted() / (1024 * 1024) + " MB");
            memoryStatus.put("percentUsed", (int)(heapMemoryUsage.getUsed() * 100.0 / heapMemoryUsage.getMax()) + "%");
            
            // System information
            ObjectNode systemInfo = JsonNodeFactory.instance.objectNode();
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("osName", System.getProperty("os.name"));
            systemInfo.put("osVersion", System.getProperty("os.version"));
            systemInfo.put("processors", Runtime.getRuntime().availableProcessors());
            
            // Add all info to result
            result.put("status", "ok");
            result.set("server", serverStatus);
            result.set("memory", memoryStatus);
            result.set("system", systemInfo);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting server status: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to get server status: " + e.getMessage());
            return result;
        }
    }
}
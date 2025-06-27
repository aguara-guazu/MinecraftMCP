package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * MCP tool for getting player list
 */
public class PlayerListTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new player list tool
     * 
     * @param plugin the plugin instance
     */
    public PlayerListTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "get_player_list";
    }
    
    @Override
    public String getDescription() {
        return "Get a list of online players";
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
            // Create player array
            ArrayNode playersArray = JsonNodeFactory.instance.arrayNode();
            
            // Add player information for each online player
            for (Player player : Bukkit.getOnlinePlayers()) {
                ObjectNode playerNode = JsonNodeFactory.instance.objectNode();
                
                // Basic player info
                playerNode.put("name", player.getName());
                playerNode.put("uuid", player.getUniqueId().toString());
                playerNode.put("displayName", player.getDisplayName());
                playerNode.put("op", player.isOp());
                
                // Location info
                Location loc = player.getLocation();
                ObjectNode locationNode = JsonNodeFactory.instance.objectNode();
                locationNode.put("world", loc.getWorld().getName());
                locationNode.put("x", Math.round(loc.getX() * 10) / 10.0); // Round to 1 decimal place
                locationNode.put("y", Math.round(loc.getY() * 10) / 10.0);
                locationNode.put("z", Math.round(loc.getZ() * 10) / 10.0);
                playerNode.set("location", locationNode);
                
                // Game info
                playerNode.put("gameMode", player.getGameMode().name());
                playerNode.put("health", Math.round(player.getHealth() * 10) / 10.0);
                playerNode.put("foodLevel", player.getFoodLevel());
                playerNode.put("level", player.getLevel());
                playerNode.put("exp", Math.round(player.getExp() * 100) / 100.0);
                
                // Online status
                playerNode.put("online", player.isOnline());
                
                // Add to players array
                playersArray.add(playerNode);
            }
            
            result.put("status", "ok");
            result.put("count", playersArray.size());
            result.set("players", playersArray);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player list: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to get player list: " + e.getMessage());
            return result;
        }
    }
}
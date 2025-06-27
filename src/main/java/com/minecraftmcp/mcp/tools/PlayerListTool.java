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
            StringBuilder playerText = new StringBuilder();
            playerText.append("=== Online Players ===\n");
            
            int playerCount = Bukkit.getOnlinePlayers().size();
            playerText.append(String.format("Players online: %d/%d\n\n", playerCount, Bukkit.getMaxPlayers()));
            
            if (playerCount == 0) {
                playerText.append("No players currently online.\n");
            } else {
                // Add player information for each online player
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerText.append(String.format("• %s", player.getName()));
                    
                    if (!player.getName().equals(player.getDisplayName())) {
                        playerText.append(String.format(" (%s)", player.getDisplayName()));
                    }
                    
                    if (player.isOp()) {
                        playerText.append(" [OP]");
                    }
                    
                    playerText.append("\n");
                    
                    // Location info
                    Location loc = player.getLocation();
                    playerText.append(String.format("  World: %s", loc.getWorld().getName()));
                    playerText.append(String.format(" (%.1f, %.1f, %.1f)\n", loc.getX(), loc.getY(), loc.getZ()));
                    
                    // Game info
                    playerText.append(String.format("  Game Mode: %s", player.getGameMode().name()));
                    playerText.append(String.format(" | Health: %.1f/20", player.getHealth()));
                    playerText.append(String.format(" | Food: %d/20", player.getFoodLevel()));
                    playerText.append(String.format(" | Level: %d", player.getLevel()));
                    playerText.append(String.format(" | XP: %.0f%%\n", player.getExp() * 100));
                    playerText.append("\n");
                }
            }
            
            // Format response according to MCP protocol
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            content.put("text", playerText.toString());
            
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting player list: " + e.getMessage());
            
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            content.put("text", "Error getting player list: " + e.getMessage());
            
            result.put("isError", true);
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
        }
    }
}
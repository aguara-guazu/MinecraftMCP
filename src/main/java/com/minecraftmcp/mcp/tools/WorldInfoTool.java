package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * MCP tool for getting world information
 */
public class WorldInfoTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new world info tool
     * 
     * @param plugin the plugin instance
     */
    public WorldInfoTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "get_world_info";
    }
    
    @Override
    public String getDescription() {
        return "Get information about Minecraft worlds";
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            String worldName = null;
            boolean includeChunks = false;
            
            // Check if specific world is requested
            if (parameters.has("world")) {
                worldName = parameters.get("world").asText();
            }
            
            // Check if chunk info is requested
            if (parameters.has("includeChunks")) {
                includeChunks = parameters.get("includeChunks").asBoolean();
            }
            
            // Create worlds array
            ArrayNode worldsArray = JsonNodeFactory.instance.arrayNode();
            
            if (worldName != null) {
                // Get specific world
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    worldsArray.add(createWorldInfo(world, includeChunks));
                } else {
                    result.put("status", "error");
                    result.put("error", "World not found: " + worldName);
                    return result;
                }
            } else {
                // Get all worlds
                for (World world : Bukkit.getWorlds()) {
                    worldsArray.add(createWorldInfo(world, includeChunks));
                }
            }
            
            result.put("status", "ok");
            result.put("count", worldsArray.size());
            result.set("worlds", worldsArray);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting world info: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to get world info: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Create world information object
     * 
     * @param world the world
     * @param includeChunks whether to include chunk information
     * @return the world information
     */
    private ObjectNode createWorldInfo(World world, boolean includeChunks) {
        ObjectNode worldNode = JsonNodeFactory.instance.objectNode();
        
        // Basic world info
        worldNode.put("name", world.getName());
        worldNode.put("uuid", world.getUID().toString());
        worldNode.put("environment", world.getEnvironment().name());
        worldNode.put("seed", world.getSeed());
        
        // World settings
        worldNode.put("difficulty", world.getDifficulty().name());
        worldNode.put("pvp", world.getPVP());
        worldNode.put("allowMonsters", world.getAllowMonsters());
        worldNode.put("allowAnimals", world.getAllowAnimals());
        
        // World state
        worldNode.put("time", world.getTime());
        worldNode.put("fullTime", world.getFullTime());
        worldNode.put("hasStorm", world.hasStorm());
        worldNode.put("thundering", world.isThundering());
        
        // World border
        WorldBorder border = world.getWorldBorder();
        ObjectNode borderNode = JsonNodeFactory.instance.objectNode();
        borderNode.put("size", border.getSize());
        borderNode.put("centerX", border.getCenter().getX());
        borderNode.put("centerZ", border.getCenter().getZ());
        borderNode.put("damageAmount", border.getDamageAmount());
        borderNode.put("damageBuffer", border.getDamageBuffer());
        worldNode.set("border", borderNode);
        
        // Entity counts
        worldNode.put("entityCount", world.getEntityCount());
        worldNode.put("livingEntityCount", world.getLivingEntities().size());
        worldNode.put("playerCount", world.getPlayers().size());
        
        // Chunk info (optional and potentially expensive)
        if (includeChunks) {
            Chunk[] loadedChunks = world.getLoadedChunks();
            worldNode.put("loadedChunkCount", loadedChunks.length);
            
            ArrayNode chunksArray = JsonNodeFactory.instance.arrayNode();
            for (Chunk chunk : loadedChunks) {
                ObjectNode chunkNode = JsonNodeFactory.instance.objectNode();
                chunkNode.put("x", chunk.getX());
                chunkNode.put("z", chunk.getZ());
                chunkNode.put("entityCount", chunk.getEntities().length);
                chunksArray.add(chunkNode);
            }
            
            worldNode.set("loadedChunks", chunksArray);
        } else {
            worldNode.put("loadedChunkCount", world.getLoadedChunks().length);
        }
        
        return worldNode;
    }
}
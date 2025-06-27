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
    public JsonNode getInputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        
        ObjectNode worldProp = JsonNodeFactory.instance.objectNode();
        worldProp.put("type", "string");
        worldProp.put("description", "Specific world name (optional)");
        properties.set("world", worldProp);
        
        ObjectNode includeChunksProp = JsonNodeFactory.instance.objectNode();
        includeChunksProp.put("type", "boolean");
        includeChunksProp.put("description", "Include chunk information");
        includeChunksProp.put("default", false);
        properties.set("includeChunks", includeChunksProp);
        
        schema.set("properties", properties);
        schema.set("required", JsonNodeFactory.instance.arrayNode());
        
        return schema;
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
            
            StringBuilder worldText = new StringBuilder();
            worldText.append("=== World Information ===\n");
            
            if (worldName != null) {
                // Get specific world
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    worldText.append(createWorldInfoText(world, includeChunks));
                } else {
                    ObjectNode content = JsonNodeFactory.instance.objectNode();
                    content.put("type", "text");
                    content.put("text", "World not found: " + worldName);
                    
                    result.put("isError", true);
                    result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
                    return result;
                }
            } else {
                // Get all worlds
                worldText.append(String.format("Number of worlds: %d\n\n", Bukkit.getWorlds().size()));
                for (World world : Bukkit.getWorlds()) {
                    worldText.append(createWorldInfoText(world, includeChunks));
                    worldText.append("\n");
                }
            }
            
            // Format response according to MCP protocol
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            content.put("text", worldText.toString());
            
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting world info: " + e.getMessage());
            
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            content.put("text", "Error getting world info: " + e.getMessage());
            
            result.put("isError", true);
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
        }
    }
    
    /**
     * Create world information text
     * 
     * @param world the world
     * @param includeChunks whether to include chunk information
     * @return the world information as text
     */
    private String createWorldInfoText(World world, boolean includeChunks) {
        StringBuilder info = new StringBuilder();
        
        info.append(String.format("World: %s\n", world.getName()));
        info.append(String.format("Environment: %s\n", world.getEnvironment().name()));
        info.append(String.format("Difficulty: %s\n", world.getDifficulty().name()));
        info.append(String.format("Seed: %d\n", world.getSeed()));
        
        info.append(String.format("PvP: %s | Monsters: %s | Animals: %s\n", 
            world.getPVP() ? "Enabled" : "Disabled",
            world.getAllowMonsters() ? "Enabled" : "Disabled", 
            world.getAllowAnimals() ? "Enabled" : "Disabled"));
        
        // Time info
        long time = world.getTime();
        String timeOfDay = time < 6000 ? "Morning" : time < 12000 ? "Day" : time < 18000 ? "Evening" : "Night";
        info.append(String.format("Time: %d (%s)\n", time, timeOfDay));
        
        // Weather
        String weather = world.hasStorm() ? (world.isThundering() ? "Thunderstorm" : "Rain") : "Clear";
        info.append(String.format("Weather: %s\n", weather));
        
        // World border
        WorldBorder border = world.getWorldBorder();
        info.append(String.format("World Border: %.0f blocks (center: %.1f, %.1f)\n", 
            border.getSize(), border.getCenter().getX(), border.getCenter().getZ()));
        
        // Entity counts
        info.append(String.format("Entities: %d total, %d living, %d players\n", 
            world.getEntityCount(), world.getLivingEntities().size(), world.getPlayers().size()));
        
        // Chunk info
        if (includeChunks) {
            Chunk[] loadedChunks = world.getLoadedChunks();
            info.append(String.format("Loaded Chunks: %d\n", loadedChunks.length));
            
            if (loadedChunks.length > 0 && loadedChunks.length <= 10) {
                info.append("Chunk Details:\n");
                for (Chunk chunk : loadedChunks) {
                    info.append(String.format("  (%d, %d) - %d entities\n", 
                        chunk.getX(), chunk.getZ(), chunk.getEntities().length));
                }
            } else if (loadedChunks.length > 10) {
                info.append("(Too many chunks to list individually)\n");
            }
        } else {
            info.append(String.format("Loaded Chunks: %d\n", world.getLoadedChunks().length));
        }
        
        return info.toString();
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
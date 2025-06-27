package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MCP tool for player management operations
 */
public class PlayerManagementTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new player management tool
     * 
     * @param plugin the plugin instance
     */
    public PlayerManagementTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "manage_player";
    }
    
    @Override
    public String getDescription() {
        return "Perform player management operations (kick, ban, teleport, etc.)";
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Get action and player name
            String action = parameters.path("action").asText();
            String playerName = parameters.path("player").asText();
            
            if (action == null || action.isEmpty()) {
                result.put("status", "error");
                result.put("error", "Action parameter is required");
                return result;
            }
            
            if (playerName == null || playerName.isEmpty()) {
                result.put("status", "error");
                result.put("error", "Player parameter is required");
                return result;
            }
            
            // Execute action
            switch (action.toLowerCase()) {
                case "kick":
                    return kickPlayer(playerName, parameters.path("reason").asText("Kicked by admin"));
                
                case "ban":
                    return banPlayer(
                        playerName, 
                        parameters.path("reason").asText("Banned by admin"), 
                        parameters.has("duration") ? parameters.path("duration").asLong() : 0
                    );
                
                case "unban":
                    return unbanPlayer(playerName);
                
                case "op":
                    return opPlayer(playerName, parameters.has("value") ? parameters.path("value").asBoolean(true) : true);
                
                case "teleport":
                    return teleportPlayer(
                        playerName,
                        parameters.path("world").asText(),
                        parameters.path("x").asDouble(),
                        parameters.path("y").asDouble(),
                        parameters.path("z").asDouble()
                    );
                
                case "teleport_to_player":
                    return teleportToPlayer(playerName, parameters.path("target").asText());
                
                case "gamemode":
                    return setGameMode(playerName, parameters.path("mode").asText());
                
                default:
                    result.put("status", "error");
                    result.put("error", "Unknown action: " + action);
                    return result;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error in player management: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Player management failed: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Kick a player from the server
     * 
     * @param playerName the player name
     * @param reason the kick reason
     * @return the result
     */
    private ObjectNode kickPlayer(String playerName, String reason) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            Player player = Bukkit.getPlayer(playerName);
            
            if (player == null || !player.isOnline()) {
                result.put("status", "error");
                result.put("error", "Player not found or not online: " + playerName);
                return result;
            }
            
            // Execute kick on main thread and wait for completion
            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    player.kickPlayer(reason);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            future.get(5, TimeUnit.SECONDS);
            
            result.put("status", "ok");
            result.put("message", "Kicked player " + playerName + " for: " + reason);
            
            return result;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            plugin.getLogger().severe("Error kicking player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to kick player: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Ban a player from the server
     * 
     * @param playerName the player name
     * @param reason the ban reason
     * @param durationMinutes the ban duration in minutes (0 for permanent)
     * @return the result
     */
    private ObjectNode banPlayer(String playerName, String reason, long durationMinutes) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            Date expiry = durationMinutes > 0 ? new Date(System.currentTimeMillis() + (durationMinutes * 60 * 1000)) : null;
            
            // Add ban
            banList.addBan(playerName, reason, expiry, "MCP Admin");
            
            // Kick if online
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        player.kickPlayer("Banned: " + reason);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                
                future.get(5, TimeUnit.SECONDS);
            }
            
            result.put("status", "ok");
            result.put("message", "Banned player " + playerName + (durationMinutes > 0 ? " for " + durationMinutes + " minutes" : " permanently"));
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error banning player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to ban player: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Unban a player
     * 
     * @param playerName the player name
     * @return the result
     */
    private ObjectNode unbanPlayer(String playerName) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            BanList banList = Bukkit.getBanList(BanList.Type.NAME);
            
            if (!banList.isBanned(playerName)) {
                result.put("status", "error");
                result.put("error", "Player is not banned: " + playerName);
                return result;
            }
            
            banList.pardon(playerName);
            
            result.put("status", "ok");
            result.put("message", "Unbanned player " + playerName);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error unbanning player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to unban player: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Grant or revoke operator status
     * 
     * @param playerName the player name
     * @param op true to grant op, false to revoke
     * @return the result
     */
    private ObjectNode opPlayer(String playerName, boolean op) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            Player player = Bukkit.getPlayer(playerName);
            
            if (player == null) {
                // Try with offline player
                Bukkit.getOfflinePlayer(playerName).setOp(op);
            } else {
                player.setOp(op);
            }
            
            result.put("status", "ok");
            result.put("message", (op ? "Granted" : "Revoked") + " operator status for " + playerName);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error " + (op ? "opping" : "deopping") + " player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to change operator status: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Teleport a player to specific coordinates
     * 
     * @param playerName the player name
     * @param worldName the world name
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return the result
     */
    private ObjectNode teleportPlayer(String playerName, String worldName, double x, double y, double z) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            Player player = Bukkit.getPlayer(playerName);
            
            if (player == null || !player.isOnline()) {
                result.put("status", "error");
                result.put("error", "Player not found or not online: " + playerName);
                return result;
            }
            
            // Get world if specified
            org.bukkit.World world = worldName != null && !worldName.isEmpty() 
                ? Bukkit.getWorld(worldName) 
                : player.getWorld();
            
            if (world == null) {
                result.put("status", "error");
                result.put("error", "World not found: " + worldName);
                return result;
            }
            
            // Create location
            Location location = new Location(world, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
            
            // Execute teleport on main thread and wait for completion
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = player.teleport(location);
                    future.complete(success);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            boolean success = future.get(5, TimeUnit.SECONDS);
            
            if (success) {
                result.put("status", "ok");
                result.put("message", "Teleported player " + playerName + " to " + world.getName() + " at " + x + ", " + y + ", " + z);
            } else {
                result.put("status", "error");
                result.put("error", "Teleport was cancelled by another plugin");
            }
            
            return result;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            plugin.getLogger().severe("Error teleporting player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to teleport player: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Teleport a player to another player
     * 
     * @param playerName the player name
     * @param targetName the target player name
     * @return the result
     */
    private ObjectNode teleportToPlayer(String playerName, String targetName) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            Player player = Bukkit.getPlayer(playerName);
            
            if (player == null || !player.isOnline()) {
                result.put("status", "error");
                result.put("error", "Player not found or not online: " + playerName);
                return result;
            }
            
            Player target = Bukkit.getPlayer(targetName);
            
            if (target == null || !target.isOnline()) {
                result.put("status", "error");
                result.put("error", "Target player not found or not online: " + targetName);
                return result;
            }
            
            // Execute teleport on main thread and wait for completion
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = player.teleport(target);
                    future.complete(success);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            boolean success = future.get(5, TimeUnit.SECONDS);
            
            if (success) {
                result.put("status", "ok");
                result.put("message", "Teleported player " + playerName + " to player " + targetName);
            } else {
                result.put("status", "error");
                result.put("error", "Teleport was cancelled by another plugin");
            }
            
            return result;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            plugin.getLogger().severe("Error teleporting player: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to teleport player: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Set a player's game mode
     * 
     * @param playerName the player name
     * @param modeName the game mode name
     * @return the result
     */
    private ObjectNode setGameMode(String playerName, String modeName) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            Player player = Bukkit.getPlayer(playerName);
            
            if (player == null || !player.isOnline()) {
                result.put("status", "error");
                result.put("error", "Player not found or not online: " + playerName);
                return result;
            }
            
            // Parse game mode
            GameMode gameMode;
            try {
                gameMode = GameMode.valueOf(modeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                result.put("status", "error");
                result.put("error", "Invalid game mode: " + modeName);
                return result;
            }
            
            // Execute game mode change on main thread and wait for completion
            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    player.setGameMode(gameMode);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            
            future.get(5, TimeUnit.SECONDS);
            
            result.put("status", "ok");
            result.put("message", "Set game mode of player " + playerName + " to " + gameMode.name());
            
            return result;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            plugin.getLogger().severe("Error changing game mode: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to change game mode: " + e.getMessage());
            return result;
        }
    }
}
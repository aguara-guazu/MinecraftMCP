package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;

/**
 * MCP tool for executing Minecraft commands
 */
public class CommandTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new command tool
     * 
     * @param plugin the plugin instance
     */
    public CommandTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "Execute a Minecraft server command";
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            String command = parameters.path("command").asText();
            
            if (command == null || command.isEmpty()) {
                result.put("status", "error");
                result.put("error", "Command parameter is required");
                return result;
            }
            
            // Check command whitelist
            if (!plugin.getSecurityManager().isCommandAllowed(command)) {
                result.put("status", "error");
                result.put("error", "Command not allowed: " + command.split(" ")[0]);
                return result;
            }
            
            // Execute command
            final String[] commandOutput = {null};
            final boolean[] commandSuccess = {false};
            
            // Execute command on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Capture command output (this is somewhat limited in Bukkit/Spigot/Paper)
                    commandSuccess[0] = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    commandOutput[0] = "Command executed";
                } catch (CommandException e) {
                    commandOutput[0] = e.getMessage();
                }
            });
            
            // Wait for command to complete (not ideal but necessary for synchronous result)
            int maxWaitMs = 1000;
            int waitMs = 0;
            while (commandOutput[0] == null && waitMs < maxWaitMs) {
                Thread.sleep(10);
                waitMs += 10;
            }
            
            // Populate result
            result.put("status", commandSuccess[0] ? "ok" : "error");
            result.put("output", commandOutput[0] != null ? commandOutput[0] : "Command timed out or failed");
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing command: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Command execution failed: " + e.getMessage());
            return result;
        }
    }
}
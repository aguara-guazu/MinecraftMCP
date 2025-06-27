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
    public JsonNode getInputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        ObjectNode commandProp = JsonNodeFactory.instance.objectNode();
        commandProp.put("type", "string");
        commandProp.put("description", "Minecraft server command to execute");
        properties.set("command", commandProp);
        
        schema.set("properties", properties);
        schema.set("required", JsonNodeFactory.instance.arrayNode().add("command"));
        
        return schema;
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            String command = parameters.path("command").asText();
            
            if (command == null || command.isEmpty()) {
                ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
                errorContent.put("type", "text");
                errorContent.put("text", "Command parameter is required");
                result.set("content", JsonNodeFactory.instance.arrayNode().add(errorContent));
                result.put("isError", true);
                return result;
            }
            
            // Check command whitelist
            if (!plugin.getSecurityManager().isCommandAllowed(command)) {
                ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
                errorContent.put("type", "text");
                errorContent.put("text", "Command not allowed: " + command.split(" ")[0]);
                result.set("content", JsonNodeFactory.instance.arrayNode().add(errorContent));
                result.put("isError", true);
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
            
            // Format result according to MCP protocol
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            
            if (commandSuccess[0]) {
                content.put("text", String.format("Command executed successfully: %s\nResult: %s", 
                    command, commandOutput[0] != null ? commandOutput[0] : "Command completed"));
            } else {
                content.put("text", String.format("Command failed: %s\nError: %s", 
                    command, commandOutput[0] != null ? commandOutput[0] : "Command timed out or failed"));
                result.put("isError", true);
            }
            
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing command: " + e.getMessage());
            ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
            errorContent.put("type", "text");
            errorContent.put("text", "Command execution failed: " + e.getMessage());
            result.set("content", JsonNodeFactory.instance.arrayNode().add(errorContent));
            result.put("isError", true);
            return result;
        }
    }
}
package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;

import java.util.List;

/**
 * MCP tool for checking available/whitelisted commands
 */
public class CommandWhitelistTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    
    /**
     * Create a new command whitelist tool
     * 
     * @param plugin the plugin instance
     */
    public CommandWhitelistTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "get_allowed_commands";
    }
    
    @Override
    public String getDescription() {
        return "Get the list of commands that are currently allowed by the server's command whitelist";
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
            // Get allowed commands from security manager
            List<String> allowedCommands = plugin.getSecurityManager().getAllowedCommandsList();
            
            // Build response
            StringBuilder response = new StringBuilder();
            response.append("Command Whitelist Status:\n\n");
            
            if (!plugin.getPluginConfig().isCommandWhitelistEnabled()) {
                response.append("🟢 Command whitelisting is DISABLED - All commands are allowed\n");
            } else if (allowedCommands.size() == 1 && allowedCommands.get(0).startsWith("ALL_COMMANDS_ALLOWED")) {
                response.append("🟢 Universal access enabled: ").append(allowedCommands.get(0)).append("\n");
            } else {
                response.append("🟡 Command whitelisting is ENABLED\n");
                response.append("Allowed commands (").append(allowedCommands.size()).append(" total):\n\n");
                
                for (String command : allowedCommands) {
                    response.append("  • ").append(command);
                    if (command.contains("*")) {
                        response.append(" (wildcard pattern)");
                    }
                    response.append("\n");
                }
                
                response.append("\nNote: Wildcard patterns support * for any characters\n");
                response.append("Example: 'ban*' matches 'ban', 'banlist', 'ban-ip', etc.\n");
            }
            
            response.append("\nTo allow all commands, add '*' to the whitelist or disable command whitelisting.");
            
            ObjectNode content = JsonNodeFactory.instance.objectNode();
            content.put("type", "text");
            content.put("text", response.toString());
            
            result.set("content", JsonNodeFactory.instance.arrayNode().add(content));
            return result;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting allowed commands: " + e.getMessage());
            ObjectNode errorContent = JsonNodeFactory.instance.objectNode();
            errorContent.put("type", "text");
            errorContent.put("text", "Failed to retrieve allowed commands: " + e.getMessage());
            result.set("content", JsonNodeFactory.instance.arrayNode().add(errorContent));
            result.put("isError", true);
            return result;
        }
    }
}
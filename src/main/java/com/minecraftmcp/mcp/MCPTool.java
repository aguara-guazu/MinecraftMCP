package com.minecraftmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface for MCP tools
 */
public interface MCPTool {
    
    /**
     * Get the tool name
     * 
     * @return the tool name
     */
    String getName();
    
    /**
     * Get the tool description
     * 
     * @return the tool description
     */
    String getDescription();
    
    /**
     * Execute the tool
     * 
     * @param parameters the tool parameters
     * @return the tool result
     */
    ObjectNode execute(JsonNode parameters);
}
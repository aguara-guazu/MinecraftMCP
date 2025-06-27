package com.minecraftmcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface for MCP resources
 */
public interface MCPResource {
    
    /**
     * Get the resource name
     * 
     * @return the resource name
     */
    String getName();
    
    /**
     * Get the resource description
     * 
     * @return the resource description
     */
    String getDescription();
    
    /**
     * Get the resource
     * 
     * @param uri the resource URI
     * @param parameters the resource parameters
     * @return the resource content
     */
    ObjectNode get(String uri, JsonNode parameters);
}
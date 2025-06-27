# MinecraftMCP

A Minecraft Paper plugin that implements the Model Context Protocol (MCP) over HTTP, enabling AI assistants like Claude Desktop to securely manage your Minecraft server.

## Overview

MinecraftMCP provides a **JSON-RPC 2.0 over HTTP** implementation of the Model Context Protocol, allowing AI assistants to interact with your Minecraft server through a standardized interface. The plugin offers secure, controlled access to server management functions through six specialized tools.

### Key Features

- **HTTP-Only MCP Implementation**: Clean JSON-RPC 2.0 over HTTP protocol
- **Secure Authentication**: API key-based authentication with session management
- **Real-time Communication**: Server-Sent Events (SSE) for live updates
- **Command Safety**: Comprehensive command whitelisting and validation
- **Rate Limiting**: Configurable request throttling and abuse protection
- **Six Core Tools**: Complete server management through specialized MCP tools

## Requirements

- **Paper Server**: 1.21.4 or later (Paper-specific features required)
- **Java**: 21 or later
- **Dependencies**: Jackson (2.15.2), Jetty (11.0.19) - auto-included via maven shade

## Installation

1. Download the plugin JAR from releases
2. Place in your server's `plugins/` directory
3. Restart your server
4. Configure the plugin by editing `plugins/MinecraftMCP/config.yml`
5. Set a secure API key in the configuration
6. Restart server to apply configuration

## Configuration

The plugin creates a comprehensive configuration file at `plugins/MinecraftMCP/config.yml`:

```yaml
# MinecraftMCP Configuration

# MCP Server settings
mcp-server:
  enabled: true
  transport: http  # HTTP-only transport
  http:
    port: 25575              # HTTP port for MCP API
    endpoint: "/mcp"         # API endpoint path
    sse-enabled: true        # Enable Server-Sent Events
    max-connections: 5       # Maximum SSE connections
    cors:
      enabled: false         # Enable CORS for web clients
      allowed-origins: "*"   # CORS allowed origins
    access-logging: false    # Log HTTP requests

# Security settings
security:
  authentication:
    api-key-enabled: true
    api-key: "change-this-to-a-secure-value"  # CHANGE THIS!
    localhost-only: true                      # Restrict to localhost
    session-timeout: 30                       # Session timeout (minutes)
  
  command-whitelist:
    enabled: true
    allowed-commands:        # Only these commands are allowed
      - "list"
      - "say"
      - "tp"
      - "kick"
      - "ban"
      - "pardon"
      - "op"
      - "deop"
      - "gamemode"
      - "time"
      - "weather"
      - "difficulty"
  
  rate-limiting:
    enabled: true
    commands-per-minute: 30   # Request rate limit
    max-auth-attempts: 5      # Failed auth attempts before ban
    temp-ban-duration: 15     # Temporary ban duration (minutes)

# Debug settings
debug:
  enabled: false
  log-level: INFO

# Feature toggles
features:
  player-tracking: true
  world-manipulation: true
  inventory-manipulation: true

# MCP Capabilities
capabilities:
  tools: true      # Enable MCP tools
  resources: true  # Enable MCP resources
  prompts: true    # Enable MCP prompts
  logging: true    # Enable MCP logging
```

## MCP Protocol Implementation

### Architecture

```
┌─────────────────┐    HTTP/JSON-RPC 2.0    ┌─────────────────┐
│  Claude Desktop │◄─────────────────────────► MinecraftMCP    │
│  (MCP Client)   │                          │  Paper Plugin   │
└─────────────────┘                          └─────────────────┘
                                                      │
                                                      ▼
                                             ┌─────────────────┐
                                             │ Minecraft Paper │
                                             │     Server      │
                                             └─────────────────┘
```

### HTTP Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/mcp` | Main JSON-RPC 2.0 MCP endpoint |
| `GET` | `/mcp/sse` | Server-Sent Events for real-time updates |

### MCP Methods

| JSON-RPC Method | Description |
|----------------|-------------|
| `initialize` | MCP handshake and capability negotiation |
| `tools/list` | List all available Minecraft management tools |
| `tools/call` | Execute a specific tool with parameters |
| `resources/list` | List available server resources (planned) |
| `resources/read` | Read server resource content (planned) |

## Available Tools

MinecraftMCP provides six specialized tools for server management:

### 1. `minecraft_execute_command`
Execute server commands with security validation.

**Parameters:**
- `command` (string, required): Minecraft command to execute

**Example:**
```json
{
  "name": "minecraft_execute_command",
  "arguments": {
    "command": "say Hello from Claude!"
  }
}
```

### 2. `minecraft_server_status`
Get comprehensive server status information.

**Parameters:** None

**Returns:** Server metrics including TPS, memory usage, player count, and uptime.

### 3. `minecraft_server_logs`
Retrieve and filter server log entries.

**Parameters:**
- `limit` (integer, optional): Maximum log entries (default: 100)
- `level` (string, optional): Log level filter (INFO, WARNING, ERROR)
- `search` (string, optional): Search term filter
- `fromTime` (string, optional): ISO timestamp filter

### 4. `minecraft_player_list`
Get detailed information about online players.

**Parameters:** None

**Returns:** Player list with locations, health, game modes, and connection info.

### 5. `minecraft_manage_player`
Perform player management operations.

**Parameters:**
- `action` (string, required): One of: kick, ban, unban, op, deop, teleport, teleport_to_player, gamemode
- `player` (string, required): Target player name
- `reason` (string, optional): Reason for kick/ban
- `duration` (integer, optional): Ban duration in minutes
- `world`, `x`, `y`, `z` (optional): Teleport coordinates
- `target_player` (string, optional): Target for teleport_to_player
- `gamemode` (string, optional): Game mode for gamemode action

**Example:**
```json
{
  "name": "minecraft_manage_player",
  "arguments": {
    "action": "teleport",
    "player": "Steve",
    "world": "world",
    "x": 100,
    "y": 64,
    "z": 200
  }
}
```

### 6. `minecraft_world_info`
Retrieve detailed world information.

**Parameters:**
- `world` (string, optional): Specific world name
- `includeChunks` (boolean, optional): Include chunk information

## Claude Desktop Integration

### Method 1: Direct HTTP Configuration

Add this to your Claude Desktop configuration file:

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows:** `%APPDATA%/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "minecraft": {
      "url": "http://localhost:25575/mcp",
      "auth": {
        "headers": {
          "X-API-Key": "your-secure-api-key-here"
        }
      }
    }
  }
}
```

### Method 2: Command-Line Configuration

You can also use a command-line approach in Claude Desktop:

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "http://localhost:25575/mcp",
        "-H", "Content-Type: application/json",
        "-H", "X-API-Key: ${MINECRAFT_API_KEY}",
        "-d", "@-"
      ],
      "env": {
        "MINECRAFT_API_KEY": "your-secure-api-key-here"
      }
    }
  }
}
```

After configuration:
1. Restart Claude Desktop
2. Start your Minecraft server with MinecraftMCP
3. Begin managing your server through Claude

## Commands

The plugin adds these in-game commands:

- `/mcp status` - Check MCP server status
- `/mcp reload` - Reload configuration (requires op)
- `/mcp start` - Start MCP server (requires op)
- `/mcp stop` - Stop MCP server (requires op)
- `/mcp help` - Show help information

## Security

### Authentication Flow
1. Client sends request with `X-API-Key` header
2. Plugin validates API key against configuration
3. Session created with UUID and timeout
4. Requests validated against session and rate limits

### Security Features
- **API Key Authentication**: Required for all requests
- **Command Whitelisting**: Only pre-approved commands allowed
- **Rate Limiting**: Configurable request throttling
- **Session Management**: Time-based session expiration
- **Localhost Restriction**: Optional localhost-only access
- **Audit Logging**: Comprehensive request/response logging

### Security Best Practices
1. **Change the default API key** to a strong, unique value
2. **Enable command whitelisting** and review allowed commands
3. **Use localhost-only** for development/testing
4. **Set up HTTPS** via reverse proxy for production
5. **Monitor logs** for suspicious activity
6. **Regular updates** to latest plugin version

## Development

### Building from Source

```bash
git clone https://github.com/aguara-guazu/MinecraftMCP.git
cd MinecraftMCP
mvn clean package
```

The compiled JAR will be in the `target/` directory.

### Testing the API

Test the MCP protocol directly with curl:

```bash
# Initialize connection
curl -X POST http://localhost:25575/mcp \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: your-api-key" \\
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "clientInfo": {"name": "test", "version": "1.0"}
    }
  }'

# List tools
curl -X POST http://localhost:25575/mcp \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: your-api-key" \\
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'

# Execute command
curl -X POST http://localhost:25575/mcp \\
  -H "Content-Type: application/json" \\
  -H "X-API-Key: your-api-key" \\
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "minecraft_execute_command",
      "arguments": {"command": "list"}
    }
  }'
```

### MCP Response Format

All tools return MCP-standard responses:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Tool response message here"
      }
    ]
  }
}
```

Error responses follow JSON-RPC 2.0:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

## Troubleshooting

### Common Issues

**Plugin doesn't start:**
- Check Java 21+ is installed
- Verify Paper 1.21.4+ compatibility
- Review server logs for dependency issues

**Authentication fails:**
- Verify API key matches configuration
- Check `localhost-only` setting if connecting remotely
- Ensure rate limits aren't exceeded

**Commands rejected:**
- Review command whitelist in configuration
- Check if command requires additional permissions
- Verify command syntax and parameters

**Connection issues:**
- Confirm port 25575 is available
- Check firewall/network restrictions
- Verify MCP server is enabled and started

### Debug Mode

Enable debug logging in config.yml:

```yaml
debug:
  enabled: true
  log-level: DEBUG
```

This provides detailed request/response logging for troubleshooting.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

For issues, questions, or feature requests:
- Open an issue on [GitHub](https://github.com/aguara-guazu/MinecraftMCP/issues)
- Check existing documentation in this README
- Review the CLAUDE.md file for development guidance

---

**Version:** 1.0.5  
**Compatible with:** Paper 1.21.4+, Java 21+  
**Protocol:** MCP 2024-11-05, JSON-RPC 2.0  
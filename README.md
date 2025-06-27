# MinecraftMCP

A Minecraft Paper plugin that exposes server terminal access through the Model Context Protocol (MCP) for remote administration via Claude Desktop.

## Overview

MinecraftMCP provides secure and controlled access to your Minecraft server through the Model Context Protocol (MCP), allowing remote administration using Claude Desktop. The plugin implements the following features:

- **Secure Authentication**: API key authentication with session management and rate limiting.
- **Command Execution**: Execute Minecraft server commands remotely.
- **Player Management**: Get player lists and manage players (kick, ban, teleport, etc.).
- **Server Monitoring**: View server status, logs, and performance metrics.
- **World Information**: Access world data and statistics.
- **Fine-grained Security Controls**: Command whitelisting and comprehensive audit logging.

## Requirements

- Paper Server 1.21.4
- Java 21 or later

## Paper Plugin Support

MinecraftMCP is built as a Paper plugin (using paper-plugin.yml) and implements Paper's command registration system. This plugin will not work with vanilla Bukkit or Spigot servers.

## Installation

1. Download the latest release from the [Releases](https://github.com/aguara-guazu/MinecraftMCP/releases) page.
2. Place the JAR file in your server's `plugins` directory.
3. Restart your server or run `/reload confirm` to load the plugin.
4. Configure the plugin by editing the `plugins/MinecraftMCP/config.yml` file.

## Configuration

After the first run, the plugin will create a `config.yml` file in the `plugins/MinecraftMCP` directory. Edit this file to customize the plugin's behavior:

### MCP Server Settings

```yaml
mcp-server:
  enabled: true
  transport: stdio
```

### Security Settings

```yaml
security:
  authentication:
    api-key-enabled: true
    api-key: "change-this-to-a-secure-value"  # Change this to a secure API key
    localhost-only: true
    session-timeout: 30  # Minutes

  command-whitelist:
    enabled: true
    allowed-commands:
      - "list"
      - "say"
      - "tp"
      - "kick"
      # Add more commands as needed

  rate-limiting:
    enabled: true
    commands-per-minute: 30
    max-auth-attempts: 5
    temp-ban-duration: 15  # Minutes
```

### Debug Settings

```yaml
debug:
  enabled: false
  log-level: INFO
```

## Commands

The plugin adds the following in-game commands:

- `/mcp status` - Check the status of the MCP server
- `/mcp reload` - Reload the plugin's configuration (requires op)
- `/mcp start` - Start the MCP server if it's not running (requires op)
- `/mcp stop` - Stop the MCP server if it's running (requires op)
- `/mcp help` - Show help information

## MCP Tool Reference

The following MCP tools are available:

### Server Tools

- `execute_command`: Execute a Minecraft server command
  - Parameters: `command` (string)
  - Example: `{"command": "say Hello from MCP"}`

- `get_server_status`: Get server status information
  - Parameters: none
  - Returns: Server version, online players, memory usage, TPS, etc.

- `get_server_logs`: Get server log entries
  - Parameters: `limit` (int, default 100), `level` (string), `search` (string), `fromTime` (string), `includeOlderLogs` (boolean)
  - Example: `{"limit": 50, "level": "WARNING", "search": "Exception"}`

### Player Tools

- `get_player_list`: Get a list of online players
  - Parameters: none
  - Returns: Array of player information including location, health, etc.

- `manage_player`: Perform player management operations
  - Parameters: `action` (kick, ban, unban, op, teleport, teleport_to_player, gamemode), `player` (string), and additional action-specific parameters
  - Examples:
    - `{"action": "kick", "player": "Steve", "reason": "AFK too long"}`
    - `{"action": "ban", "player": "Steve", "reason": "Griefing", "duration": 60}`
    - `{"action": "teleport", "player": "Steve", "world": "world", "x": 100, "y": 64, "z": 100}`

### World Tools

- `get_world_info`: Get world information
  - Parameters: `world` (string, optional), `includeChunks` (boolean, default false)
  - Example: `{"world": "world_nether", "includeChunks": true}`

## Claude Desktop Integration

To integrate MinecraftMCP with Claude Desktop, follow these steps:

1. Configure Claude Desktop for MCP support
2. Create a new MCP connection using stdio transport
3. Configure the API key authentication
4. Connect and begin interacting with your Minecraft server

## Security Considerations

- **Change the default API key** to a strong, unique value
- Enable command whitelisting and only allow necessary commands
- Keep the plugin updated to the latest version
- Consider using IP restrictions for additional security
- Regularly review audit logs for suspicious activity

## Building from Source

```bash
git clone https://github.com/aguara-guazu/MinecraftMCP.git
cd MinecraftMCP
mvn clean package
```

The compiled JAR will be in the `target` directory.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

If you encounter any issues or have questions, please [open an issue](https://github.com/aguara-guazu/MinecraftMCP/issues) on GitHub.
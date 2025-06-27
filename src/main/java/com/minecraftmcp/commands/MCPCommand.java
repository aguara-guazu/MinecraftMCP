package com.minecraftmcp.commands;

import com.minecraftmcp.MinecraftMCPPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for MinecraftMCP
 */
public class MCPCommand implements CommandExecutor, TabCompleter {

    private final MinecraftMCPPlugin plugin;
    private final List<String> subcommands = Arrays.asList("status", "reload", "start", "stop", "help");
    
    /**
     * Create a new MCP command handler
     * 
     * @param plugin the plugin instance
     */
    public MCPCommand(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                if (!sender.hasPermission("minecraftmcp.commands.status")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                
                boolean isRunning = plugin.getMcpServer() != null && plugin.getMcpServer().isRunning();
                sender.sendMessage(ChatColor.GREEN + "MCP Server status: " + 
                    (isRunning ? ChatColor.GREEN + "RUNNING" : ChatColor.RED + "STOPPED"));
                if (isRunning) {
                    sender.sendMessage(ChatColor.GREEN + "Transport: " + plugin.getPluginConfig().getMcpServerTransport());
                }
                break;
                
            case "reload":
                if (!sender.hasPermission("minecraftmcp.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "MinecraftMCP configuration reloaded!");
                break;
                
            case "start":
                if (!sender.hasPermission("minecraftmcp.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                
                if (plugin.getMcpServer() != null && plugin.getMcpServer().isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "MCP Server is already running!");
                    return true;
                }
                
                if (plugin.getMcpServer() == null) {
                    plugin.getMcpServer().start();
                    sender.sendMessage(ChatColor.GREEN + "MCP Server started!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to start MCP Server. Check logs for details.");
                }
                break;
                
            case "stop":
                if (!sender.hasPermission("minecraftmcp.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                
                if (plugin.getMcpServer() == null || !plugin.getMcpServer().isRunning()) {
                    sender.sendMessage(ChatColor.YELLOW + "MCP Server is not running!");
                    return true;
                }
                
                plugin.getMcpServer().stop();
                sender.sendMessage(ChatColor.GREEN + "MCP Server stopped!");
                break;
                
            case "help":
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * Send help information to a command sender
     * 
     * @param sender the command sender
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "---- MinecraftMCP Help ----");
        sender.sendMessage(ChatColor.YELLOW + "/mcp status " + ChatColor.WHITE + "- Show MCP server status");
        if (sender.hasPermission("minecraftmcp.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/mcp reload " + ChatColor.WHITE + "- Reload the configuration");
            sender.sendMessage(ChatColor.YELLOW + "/mcp start " + ChatColor.WHITE + "- Start the MCP server");
            sender.sendMessage(ChatColor.YELLOW + "/mcp stop " + ChatColor.WHITE + "- Stop the MCP server");
        }
        sender.sendMessage(ChatColor.YELLOW + "/mcp help " + ChatColor.WHITE + "- Show this help message");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
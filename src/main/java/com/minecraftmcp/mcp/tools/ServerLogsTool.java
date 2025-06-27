package com.minecraftmcp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minecraftmcp.MinecraftMCPPlugin;
import com.minecraftmcp.mcp.MCPTool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MCP tool for accessing server logs
 */
public class ServerLogsTool implements MCPTool {
    
    private final MinecraftMCPPlugin plugin;
    private final Pattern logPattern = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})\\] \\[(.*?)\\/(.*)\\]: (.*)$");
    
    /**
     * Create a new server logs tool
     * 
     * @param plugin the plugin instance
     */
    public ServerLogsTool(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public String getName() {
        return "get_server_logs";
    }
    
    @Override
    public String getDescription() {
        return "Get server log entries";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        
        ObjectNode properties = JsonNodeFactory.instance.objectNode();
        
        ObjectNode limitProp = JsonNodeFactory.instance.objectNode();
        limitProp.put("type", "integer");
        limitProp.put("description", "Maximum number of log entries to return");
        limitProp.put("default", 100);
        properties.set("limit", limitProp);
        
        ObjectNode levelProp = JsonNodeFactory.instance.objectNode();
        levelProp.put("type", "string");
        levelProp.put("description", "Log level filter (INFO, WARNING, ERROR)");
        levelProp.set("enum", JsonNodeFactory.instance.arrayNode().add("INFO").add("WARNING").add("ERROR"));
        properties.set("level", levelProp);
        
        ObjectNode searchProp = JsonNodeFactory.instance.objectNode();
        searchProp.put("type", "string");
        searchProp.put("description", "Search term to filter logs");
        properties.set("search", searchProp);
        
        ObjectNode fromTimeProp = JsonNodeFactory.instance.objectNode();
        fromTimeProp.put("type", "string");
        fromTimeProp.put("description", "ISO timestamp to filter logs from");
        properties.set("fromTime", fromTimeProp);
        
        ObjectNode includeOlderProp = JsonNodeFactory.instance.objectNode();
        includeOlderProp.put("type", "boolean");
        includeOlderProp.put("description", "Include older log files");
        includeOlderProp.put("default", false);
        properties.set("includeOlderLogs", includeOlderProp);
        
        schema.set("properties", properties);
        schema.set("required", JsonNodeFactory.instance.arrayNode());
        
        return schema;
    }
    
    @Override
    public ObjectNode execute(JsonNode parameters) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        
        try {
            // Parse parameters
            int limit = parameters.has("limit") ? parameters.get("limit").asInt(100) : 100;
            String level = parameters.has("level") ? parameters.get("level").asText() : null;
            String search = parameters.has("search") ? parameters.get("search").asText() : null;
            String fromTime = parameters.has("fromTime") ? parameters.get("fromTime").asText() : null;
            boolean includeOlderLogs = parameters.has("includeOlderLogs") && parameters.get("includeOlderLogs").asBoolean();
            
            // Get logs
            List<LogEntry> logs = getLogEntries(limit, level, search, fromTime, includeOlderLogs);
            
            // Create log array
            ArrayNode logsArray = JsonNodeFactory.instance.arrayNode();
            
            for (LogEntry logEntry : logs) {
                ObjectNode entryNode = JsonNodeFactory.instance.objectNode();
                entryNode.put("time", logEntry.getTime());
                entryNode.put("thread", logEntry.getThread());
                entryNode.put("level", logEntry.getLevel());
                entryNode.put("message", logEntry.getMessage());
                logsArray.add(entryNode);
            }
            
            result.put("status", "ok");
            result.put("count", logsArray.size());
            result.set("logs", logsArray);
            
            return result;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting server logs: " + e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to get server logs: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * Get log entries
     * 
     * @param limit the maximum number of entries
     * @param level the log level filter
     * @param search the search term
     * @param fromTime the start time
     * @param includeOlderLogs whether to include older log files
     * @return the log entries
     */
    private List<LogEntry> getLogEntries(int limit, String level, String search, String fromTime, boolean includeOlderLogs) throws IOException, ParseException {
        List<LogEntry> entries = new ArrayList<>();
        
        // Determine server logs directory
        File logsDir = getLogsDirectory();
        
        if (logsDir == null || !logsDir.exists() || !logsDir.isDirectory()) {
            plugin.getLogger().warning("Could not find server logs directory");
            return Collections.emptyList();
        }
        
        // Get log files
        List<File> logFiles = new ArrayList<>();
        
        // Current log file is always first
        File latestLog = new File(logsDir, "latest.log");
        if (latestLog.exists() && latestLog.isFile()) {
            logFiles.add(latestLog);
        }
        
        // Add older logs if requested
        if (includeOlderLogs) {
            try {
                logFiles.addAll(
                    Files.list(logsDir.toPath())
                        .filter(path -> path.toString().endsWith(".log.gz") || path.toString().matches(".*\\d{4}-\\d{2}-\\d{2}-\\d+\\.log\\.gz"))
                        .map(Path::toFile)
                        .sorted(Collections.reverseOrder())
                        .collect(Collectors.toList())
                );
            } catch (IOException e) {
                plugin.getLogger().warning("Error listing log files: " + e.getMessage());
            }
        }
        
        // Parse from time
        Date parsedFromTime = null;
        if (fromTime != null && !fromTime.isEmpty()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
            parsedFromTime = dateFormat.parse(fromTime);
        }
        
        // Parse log files
        for (File logFile : logFiles) {
            if (entries.size() >= limit) {
                break;
            }
            
            if (logFile.getName().equals("latest.log")) {
                // Parse latest log file
                entries.addAll(parseLogFile(logFile, limit - entries.size(), level, search, parsedFromTime));
            } else {
                // TODO: Add support for compressed logs if needed
                // This would require decompressing log.gz files
            }
        }
        
        return entries;
    }
    
    /**
     * Parse a log file
     * 
     * @param logFile the log file
     * @param limit the maximum number of entries
     * @param levelFilter the log level filter
     * @param search the search term
     * @param fromTime the start time
     * @return the log entries
     */
    private List<LogEntry> parseLogFile(File logFile, int limit, String levelFilter, String search, Date fromTime) throws IOException, ParseException {
        List<LogEntry> entries = new ArrayList<>();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        
        // Read log file from end to get most recent entries first
        List<String> lines = Files.readAllLines(logFile.toPath());
        Collections.reverse(lines);
        
        for (String line : lines) {
            if (entries.size() >= limit) {
                break;
            }
            
            Matcher matcher = logPattern.matcher(line);
            if (matcher.find()) {
                String time = matcher.group(1);
                String thread = matcher.group(2);
                String level = matcher.group(3);
                String message = matcher.group(4);
                
                // Apply filters
                if (levelFilter != null && !levelFilter.equalsIgnoreCase(level)) {
                    continue;
                }
                
                if (search != null && !message.toLowerCase().contains(search.toLowerCase())) {
                    continue;
                }
                
                if (fromTime != null) {
                    Date logTime = timeFormat.parse(time);
                    if (logTime.before(fromTime)) {
                        continue;
                    }
                }
                
                entries.add(new LogEntry(time, thread, level, message));
            }
        }
        
        return entries;
    }
    
    /**
     * Get the server logs directory
     * 
     * @return the logs directory
     */
    private File getLogsDirectory() {
        // Try to find logs directory in server root
        File serverDir = new File(".").getAbsoluteFile();
        File logsDir = new File(serverDir, "logs");
        
        if (logsDir.exists() && logsDir.isDirectory()) {
            return logsDir;
        }
        
        // Try parent directory (in case plugin directory is different)
        logsDir = new File(serverDir.getParentFile(), "logs");
        if (logsDir.exists() && logsDir.isDirectory()) {
            return logsDir;
        }
        
        // Not found
        return null;
    }
    
    /**
     * Log entry data class
     */
    private static class LogEntry {
        private final String time;
        private final String thread;
        private final String level;
        private final String message;
        
        public LogEntry(String time, String thread, String level, String message) {
            this.time = time;
            this.thread = thread;
            this.level = level;
            this.message = message;
        }
        
        public String getTime() {
            return time;
        }
        
        public String getThread() {
            return thread;
        }
        
        public String getLevel() {
            return level;
        }
        
        public String getMessage() {
            return message;
        }
    }
}
package com.minecraftmcp.security;

import com.minecraftmcp.MinecraftMCPPlugin;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Security manager for MinecraftMCP
 */
public class SecurityManager {
    
    private final MinecraftMCPPlugin plugin;
    
    // Track authentication attempts by source
    private final Map<String, AuthAttemptTracker> authAttempts = new ConcurrentHashMap<>();
    
    // Track active sessions
    private final Map<UUID, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    
    // Command pattern cache for wildcards
    private final Map<String, Pattern> commandPatterns = new HashMap<>();
    
    /**
     * Create a new security manager
     * 
     * @param plugin the plugin instance
     */
    public SecurityManager(MinecraftMCPPlugin plugin) {
        this.plugin = plugin;
        
        // Initialize command patterns for whitelisting
        initCommandPatterns();
        
        // Schedule session cleanup
        scheduleSessionCleanup();
    }
    
    /**
     * Initialize command patterns from config
     */
    private void initCommandPatterns() {
        commandPatterns.clear();
        for (String command : plugin.getPluginConfig().getAllowedCommands()) {
            // Convert wildcards to regex patterns
            String pattern = "^" + command.replace("*", ".*") + "$";
            commandPatterns.put(command, Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        }
        
        if (plugin.getPluginConfig().isDebugEnabled()) {
            plugin.getLogger().info("Initialized " + commandPatterns.size() + " command patterns");
            for (String cmd : commandPatterns.keySet()) {
                plugin.getLogger().info("  - Pattern: " + cmd);
            }
        }
    }
    
    /**
     * Reload command patterns from config (for live config updates)
     */
    public void reloadCommandPatterns() {
        plugin.getLogger().info("Reloading command patterns from config...");
        initCommandPatterns();
    }
    
    /**
     * Schedule periodic session cleanup
     */
    private void scheduleSessionCleanup() {
        int sessionTimeout = plugin.getPluginConfig().getSessionTimeout();
        if (sessionTimeout > 0) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                long currentTime = System.currentTimeMillis();
                activeSessions.entrySet().removeIf(entry -> {
                    SessionInfo info = entry.getValue();
                    return (currentTime - info.getLastActivity()) > (sessionTimeout * 60 * 1000);
                });
            }, 20 * 60, 20 * 60); // Run every minute
        }
    }
    
    /**
     * Validate API key authentication
     * 
     * @param apiKey the API key to validate
     * @param source the source of the authentication attempt
     * @return true if authenticated, false otherwise
     */
    public boolean validateApiKey(String apiKey, String source) {
        // Check if API key auth is enabled
        if (!plugin.getPluginConfig().isApiKeyAuthEnabled()) {
            plugin.getLogger().warning("API key authentication is disabled, but an attempt was made from " + source);
            return false;
        }
        
        // Check rate limiting
        if (plugin.getPluginConfig().isRateLimitingEnabled() && isRateLimited(source)) {
            plugin.getLogger().warning("Rate limit exceeded for authentication attempts from " + source);
            return false;
        }
        
        // Validate API key
        boolean isValid = plugin.getPluginConfig().getApiKey().equals(apiKey);
        
        // Track attempt
        trackAuthAttempt(source, isValid);
        
        // Log attempt
        if (isValid) {
            plugin.getLogger().info("Successful API key authentication from " + source);
        } else {
            plugin.getLogger().warning("Failed API key authentication attempt from " + source);
        }
        
        return isValid;
    }
    
    /**
     * Create a new session
     * 
     * @param source the source of the session
     * @return the session ID
     */
    public UUID createSession(String source) {
        UUID sessionId = UUID.randomUUID();
        SessionInfo sessionInfo = new SessionInfo(source);
        activeSessions.put(sessionId, sessionInfo);
        
        plugin.getLogger().info("Created new session " + sessionId + " for " + source);
        
        return sessionId;
    }
    
    /**
     * Validate a session
     * 
     * @param sessionId the session ID
     * @return true if valid, false otherwise
     */
    public boolean validateSession(UUID sessionId) {
        SessionInfo sessionInfo = activeSessions.get(sessionId);
        
        if (sessionInfo == null) {
            return false;
        }
        
        // Update last activity
        sessionInfo.updateActivity();
        
        return true;
    }
    
    /**
     * End a session
     * 
     * @param sessionId the session ID
     */
    public void endSession(UUID sessionId) {
        SessionInfo sessionInfo = activeSessions.remove(sessionId);
        
        if (sessionInfo != null) {
            plugin.getLogger().info("Ended session " + sessionId + " for " + sessionInfo.getSource());
        }
    }
    
    /**
     * Check if a command is allowed
     * 
     * @param command the command to check
     * @return true if allowed, false otherwise
     */
    public boolean isCommandAllowed(String command) {
        // If command whitelisting is disabled, allow all commands
        if (!plugin.getPluginConfig().isCommandWhitelistEnabled()) {
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Command whitelist disabled - allowing: " + command);
            }
            return true;
        }
        
        // Extract base command (remove arguments)
        String baseCommand = command.split(" ")[0];
        
        // Check for universal wildcard - if "*" is in the allowed commands, allow everything
        List<String> allowedCommands = plugin.getPluginConfig().getAllowedCommands();
        if (allowedCommands.contains("*")) {
            if (plugin.getPluginConfig().isDebugEnabled()) {
                plugin.getLogger().info("Universal wildcard (*) found - allowing: " + baseCommand);
            }
            return true;
        }
        
        // Check against patterns
        for (Map.Entry<String, Pattern> entry : commandPatterns.entrySet()) {
            if (entry.getValue().matcher(baseCommand).matches()) {
                if (plugin.getPluginConfig().isDebugEnabled()) {
                    plugin.getLogger().info("Command '" + baseCommand + "' matched pattern '" + entry.getKey() + "'");
                }
                return true;
            }
        }
        
        // Not in whitelist
        if (plugin.getPluginConfig().isDebugEnabled()) {
            plugin.getLogger().info("Command '" + baseCommand + "' did not match any patterns. Available patterns: " + 
                String.join(", ", commandPatterns.keySet()));
        }
        plugin.getLogger().warning("Command not in whitelist: " + baseCommand);
        return false;
    }
    
    /**
     * Get list of allowed commands for MCP tools
     * 
     * @return list of allowed commands or special message if universal access
     */
    public List<String> getAllowedCommandsList() {
        if (!plugin.getPluginConfig().isCommandWhitelistEnabled()) {
            return List.of("ALL_COMMANDS_ALLOWED - Command whitelist is disabled");
        }
        
        List<String> allowedCommands = plugin.getPluginConfig().getAllowedCommands();
        if (allowedCommands.contains("*")) {
            return List.of("ALL_COMMANDS_ALLOWED - Universal wildcard (*) is configured");
        }
        
        return allowedCommands;
    }
    
    /**
     * Track an authentication attempt
     * 
     * @param source the source of the attempt
     * @param success whether the attempt was successful
     */
    private void trackAuthAttempt(String source, boolean success) {
        AuthAttemptTracker tracker = authAttempts.computeIfAbsent(source, s -> new AuthAttemptTracker());
        
        if (success) {
            // Reset failed attempts on success
            tracker.reset();
        } else {
            // Increment failed attempts
            tracker.addFailedAttempt();
            
            // Check if we should ban
            int maxAttempts = plugin.getPluginConfig().getMaxAuthAttempts();
            if (maxAttempts > 0 && tracker.getFailedAttempts() >= maxAttempts) {
                // Ban the source
                long banTime = System.currentTimeMillis() + (plugin.getPluginConfig().getTempBanDuration() * 60 * 1000);
                tracker.setBanUntil(banTime);
                
                plugin.getLogger().warning("Temporarily banned " + source + " due to excessive failed auth attempts");
            }
        }
    }
    
    /**
     * Check if a source is rate limited
     * 
     * @param source the source to check
     * @return true if rate limited, false otherwise
     */
    private boolean isRateLimited(String source) {
        AuthAttemptTracker tracker = authAttempts.get(source);
        
        if (tracker != null) {
            // Check if banned
            if (tracker.isBanned()) {
                return true;
            }
            
            // Check rate limiting
            return tracker.isRateLimited();
        }
        
        return false;
    }
    
    /**
     * Session information
     */
    private static class SessionInfo {
        private final String source;
        private long lastActivity;
        
        public SessionInfo(String source) {
            this.source = source;
            this.lastActivity = System.currentTimeMillis();
        }
        
        public String getSource() {
            return source;
        }
        
        public long getLastActivity() {
            return lastActivity;
        }
        
        public void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }
    }
    
    /**
     * Authentication attempt tracker
     */
    private static class AuthAttemptTracker {
        private int failedAttempts;
        private long lastAttempt;
        private long banUntil;
        
        public AuthAttemptTracker() {
            this.failedAttempts = 0;
            this.lastAttempt = System.currentTimeMillis();
            this.banUntil = 0;
        }
        
        public void addFailedAttempt() {
            failedAttempts++;
            lastAttempt = System.currentTimeMillis();
        }
        
        public void reset() {
            failedAttempts = 0;
            lastAttempt = System.currentTimeMillis();
        }
        
        public int getFailedAttempts() {
            return failedAttempts;
        }
        
        public void setBanUntil(long banUntil) {
            this.banUntil = banUntil;
        }
        
        public boolean isBanned() {
            return System.currentTimeMillis() < banUntil;
        }
        
        public boolean isRateLimited() {
            // Check if too many attempts in last minute
            return (System.currentTimeMillis() - lastAttempt) < 1000;
        }
    }
}
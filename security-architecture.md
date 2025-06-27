# Minecraft MCP Security Architecture

This document outlines a comprehensive security framework for the Minecraft MCP (Minecraft Control Panel) plugin. The framework addresses key security concerns including authentication and authorization, command security, audit logging, rate limiting, and other security best practices.

## Table of Contents

1. [Security Overview](#security-overview)
2. [Authentication & Authorization](#authentication--authorization)
3. [Command Whitelisting & Permission Management](#command-whitelisting--permission-management)
4. [Audit Logging System](#audit-logging-system)
5. [Rate Limiting & Abuse Protection](#rate-limiting--abuse-protection)
6. [Configuration Options](#configuration-options)
7. [Implementation Patterns & Examples](#implementation-patterns--examples)
8. [Security Testing & Validation](#security-testing--validation)

## Security Overview

The MCP Security Framework is designed with a defense-in-depth approach, implementing multiple layers of security controls to protect Minecraft servers from unauthorized access, command abuse, and exploitation attempts. The framework follows these key security principles:

- **Principle of Least Privilege**: Users are granted only the permissions they absolutely need.
- **Defense in Depth**: Multiple security layers working together for comprehensive protection.
- **Secure by Default**: Default configuration emphasizes security over convenience.
- **Auditability**: All security-relevant actions are logged and traceable.
- **Separation of Concerns**: Different security functions are modularized for better maintainability.

## Authentication & Authorization

### Authentication Mechanisms

The MCP plugin supports multiple authentication methods:

1. **Minecraft Account Authentication**
   - Leverages Minecraft's built-in authentication system
   - Validates against Mojang/Microsoft accounts
   - Configurable to work in both online and offline server modes

2. **Two-Factor Authentication (2FA)**
   - Time-based one-time password (TOTP) support
   - Integration with authenticator apps (Google Authenticator, Authy)
   - Recovery codes for backup access

3. **API Key Authentication**
   - For external systems integrating with MCP
   - Revocable, rotatable credentials
   - Scoped permissions for fine-grained control

4. **IP-Based Authentication**
   - Whitelist trusted IP addresses
   - IP range support with CIDR notation
   - Automatic temporary blocking for suspicious IPs

### Authorization Model

MCP implements a role-based access control (RBAC) system with:

1. **Predefined Roles**:
   - `mcp.admin`: Full administrative access
   - `mcp.moderator`: Command execution without configuration changes
   - `mcp.user`: Limited read-only access
   - `mcp.api`: API-only access for integrations

2. **Custom Role Definition**:
   - JSON-based role configuration
   - Inheritance support for role hierarchies
   - Fine-grained permission assignments

3. **Context-Based Permissions**:
   - Time-based access restrictions
   - Server-state dependent permissions
   - Resource utilization thresholds

### Implementation Example

```java
// User authentication service
public class McpAuthenticationService {
    
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final SessionManager sessionManager;
    
    public AuthenticationResult authenticate(String username, String password, String totpCode) {
        User user = userRepository.findByUsername(username);
        
        // Verify user exists and password matches
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditLogger.log(AuditEvent.FAILED_LOGIN, username);
            return AuthenticationResult.failure("Invalid username or password");
        }
        
        // Check if 2FA is required
        if (user.isTwoFactorEnabled()) {
            if (totpCode == null || !totpService.verifyCode(user.getTotpSecret(), totpCode)) {
                auditLogger.log(AuditEvent.FAILED_2FA, username);
                return AuthenticationResult.failure("Invalid 2FA code");
            }
        }
        
        // Create and return session
        String sessionToken = sessionManager.createSession(user);
        auditLogger.log(AuditEvent.SUCCESSFUL_LOGIN, username);
        return AuthenticationResult.success(sessionToken, user);
    }
}

// Authorization service
public class McpAuthorizationService {
    
    private final RoleRepository roleRepository;
    
    public boolean hasPermission(User user, String permission) {
        // Check if user has the specific permission
        for (Role role : user.getRoles()) {
            if (roleHasPermission(role, permission)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean roleHasPermission(Role role, String permission) {
        // Check direct permission
        if (role.getPermissions().contains(permission)) {
            return true;
        }
        
        // Check permission patterns (wildcards)
        for (String rolePermission : role.getPermissions()) {
            if (isPermissionMatch(rolePermission, permission)) {
                return true;
            }
        }
        
        // Check parent roles recursively
        for (Role parentRole : role.getParentRoles()) {
            if (roleHasPermission(parentRole, permission)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isPermissionMatch(String pattern, String permission) {
        // Handle wildcard permissions (e.g., "mcp.commands.*")
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return permission.startsWith(prefix);
        }
        return pattern.equals(permission);
    }
}
```

## Command Whitelisting & Permission Management

### Command Security Structure

1. **Command Registration System**:
   - All commands must be explicitly registered
   - Command metadata includes security requirements
   - Runtime validation of command parameters

2. **Permission Nodes**:
   - Hierarchical permission structure
   - `mcp.command.<category>.<command>` format
   - Additional nodes for specific parameters/options

3. **Command Whitelisting**:
   - Global whitelist configuration option
   - Per-server command restrictions
   - Environment-based command availability

4. **Execution Context**:
   - Validated command source (player, console, API)
   - Server state validation before execution
   - Resource threshold checks

### Implementation Example

```java
// Command registration
public class CommandRegistry {

    private final Map<String, CommandHandler> registeredCommands = new HashMap<>();
    private final AuthorizationService authService;
    
    public void registerCommand(String name, CommandHandler handler, String requiredPermission) {
        CommandMetadata metadata = new CommandMetadata(name, requiredPermission);
        registeredCommands.put(name, new SecureCommandHandler(handler, metadata, authService));
    }
    
    public boolean executeCommand(User user, String commandName, Map<String, Object> parameters) {
        CommandHandler handler = registeredCommands.get(commandName);
        
        if (handler == null) {
            return false;
        }
        
        // Check whitelist
        if (!isCommandWhitelisted(commandName)) {
            auditLogger.log(AuditEvent.BLOCKED_COMMAND, user.getUsername(), commandName);
            return false;
        }
        
        return handler.execute(user, parameters);
    }
    
    private boolean isCommandWhitelisted(String commandName) {
        // Check global whitelist
        if (!configService.isCommandGloballyWhitelisted(commandName)) {
            return false;
        }
        
        // Check server-specific whitelist
        String currentServer = serverContextProvider.getCurrentServer();
        return configService.isCommandWhitelistedForServer(commandName, currentServer);
    }
}

// Secure command handler wrapper
public class SecureCommandHandler implements CommandHandler {
    
    private final CommandHandler delegate;
    private final CommandMetadata metadata;
    private final AuthorizationService authService;
    
    @Override
    public boolean execute(User user, Map<String, Object> parameters) {
        // Check permission
        if (!authService.hasPermission(user, metadata.getRequiredPermission())) {
            auditLogger.log(AuditEvent.PERMISSION_DENIED, 
                           user.getUsername(), 
                           metadata.getName(), 
                           metadata.getRequiredPermission());
            return false;
        }
        
        // Validate parameters
        if (!validateParameters(parameters)) {
            auditLogger.log(AuditEvent.INVALID_COMMAND_PARAMETERS, 
                           user.getUsername(), 
                           metadata.getName(), 
                           parameters.toString());
            return false;
        }
        
        // Execute the command
        auditLogger.log(AuditEvent.COMMAND_EXECUTION, 
                       user.getUsername(), 
                       metadata.getName(), 
                       parameters.toString());
        
        return delegate.execute(user, parameters);
    }
    
    private boolean validateParameters(Map<String, Object> parameters) {
        // Implement parameter validation logic
        // Check for required parameters, type validation, etc.
        return true;
    }
}
```

## Audit Logging System

### Audit Log Structure

1. **Event Categories**:
   - Authentication events
   - Authorization events
   - Command execution events
   - Configuration changes
   - Server state changes

2. **Log Entry Fields**:
   - Timestamp with millisecond precision
   - Event category and type
   - User identifier
   - Session identifier
   - Source IP address
   - Command/action details
   - Target resources
   - Operation result

3. **Storage Options**:
   - Local file logging with rotation
   - Database logging
   - External syslog integration
   - Real-time monitoring alerts

### Implementation Example

```java
// Audit logger
public class AuditLogger {
    
    private final LogStorage storage;
    private final EventEnricher enricher;
    
    public void log(AuditEvent eventType, String username, Object... details) {
        AuditLogEntry entry = new AuditLogEntry();
        
        // Set basic information
        entry.setTimestamp(System.currentTimeMillis());
        entry.setEventType(eventType);
        entry.setUsername(username);
        
        // Get current session and context information
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getSession() != null) {
            entry.setSessionId(context.getSession().getId());
            entry.setSourceIp(context.getSession().getSourceIp());
        }
        
        // Add event-specific details
        entry.setDetails(formatDetails(details));
        
        // Enrich with additional context
        enricher.enrichLogEntry(entry);
        
        // Store the log entry
        storage.store(entry);
        
        // Check if this event requires real-time alerts
        if (shouldTriggerAlert(eventType, username, details)) {
            alertService.sendAlert(entry);
        }
    }
    
    private boolean shouldTriggerAlert(AuditEvent eventType, String username, Object... details) {
        // Implement alert triggering logic
        return eventType.isCritical() || 
               isSuspiciousActivity(eventType, username) ||
               isConfigurationChange(eventType);
    }
    
    private String formatDetails(Object... details) {
        // Format details into structured log format (JSON)
        return JsonUtils.toJson(details);
    }
}

// Define audit events
public enum AuditEvent {
    // Authentication events
    SUCCESSFUL_LOGIN(Category.AUTHENTICATION, Severity.INFO),
    FAILED_LOGIN(Category.AUTHENTICATION, Severity.WARNING),
    FAILED_2FA(Category.AUTHENTICATION, Severity.WARNING),
    LOGOUT(Category.AUTHENTICATION, Severity.INFO),
    PASSWORD_CHANGE(Category.AUTHENTICATION, Severity.INFO),
    
    // Authorization events
    PERMISSION_DENIED(Category.AUTHORIZATION, Severity.WARNING),
    ROLE_ASSIGNED(Category.AUTHORIZATION, Severity.INFO),
    ROLE_REMOVED(Category.AUTHORIZATION, Severity.INFO),
    
    // Command events
    COMMAND_EXECUTION(Category.COMMAND, Severity.INFO),
    BLOCKED_COMMAND(Category.COMMAND, Severity.WARNING),
    INVALID_COMMAND_PARAMETERS(Category.COMMAND, Severity.WARNING),
    
    // Configuration events
    CONFIG_CHANGE(Category.CONFIGURATION, Severity.INFO),
    SECURITY_CONFIG_CHANGE(Category.CONFIGURATION, Severity.WARNING),
    
    // Server events
    SERVER_START(Category.SERVER, Severity.INFO),
    SERVER_STOP(Category.SERVER, Severity.INFO);
    
    private final Category category;
    private final Severity severity;
    
    // Constructor, getters, etc.
    
    public boolean isCritical() {
        return severity == Severity.CRITICAL;
    }
    
    public enum Category {
        AUTHENTICATION, AUTHORIZATION, COMMAND, CONFIGURATION, SERVER
    }
    
    public enum Severity {
        INFO, WARNING, CRITICAL
    }
}
```

## Rate Limiting & Abuse Protection

### Rate Limiting Implementation

1. **Token Bucket Algorithm**:
   - Configurable token refill rate
   - Per-user bucket sizing
   - Role-based bucket adjustments

2. **Limit Categories**:
   - Authentication attempts
   - Command executions
   - API requests
   - Resource-intensive operations

3. **Graduated Response**:
   - Warning on approaching limits
   - Temporary throttling
   - Automatic temporary bans
   - Admin notifications for repeat offenders

### Abuse Detection

1. **Behavior Analysis**:
   - Pattern matching for command abuse
   - Unusual timing detection
   - Concurrent session monitoring

2. **Automatic Protection**:
   - IP blocking for repeated failures
   - Captcha challenges for suspicious activities
   - Temporary account lockouts

### Implementation Example

```java
// Rate limiter service
public class RateLimiterService {
    
    private final Map<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();
    private final ConfigurationService configService;
    
    public boolean checkRateLimit(String username, RateLimitCategory category) {
        TokenBucket bucket = getUserBucket(username, category);
        
        // Try to consume a token
        boolean allowed = bucket.tryConsume();
        
        // Log rate limit events
        if (!allowed) {
            auditLogger.log(AuditEvent.RATE_LIMIT_EXCEEDED, username, category.name());
            
            // Check for abuse patterns
            if (isAbuseSuspected(username, category)) {
                securityIncidentHandler.handleSuspectedAbuse(username, category);
            }
        }
        
        return allowed;
    }
    
    private TokenBucket getUserBucket(String username, RateLimitCategory category) {
        String bucketKey = username + ":" + category.name();
        
        return userBuckets.computeIfAbsent(bucketKey, key -> {
            // Create new bucket with configuration based on user role and category
            User user = userRepository.findByUsername(username);
            int capacity = configService.getRateLimitCapacity(user.getRole(), category);
            double refillRate = configService.getRateLimitRefillRate(user.getRole(), category);
            
            return new TokenBucket(capacity, refillRate);
        });
    }
    
    private boolean isAbuseSuspected(String username, RateLimitCategory category) {
        // Check recent rate limit violations
        int recentViolations = violationRepository.countRecentViolations(username, category, Duration.ofMinutes(10));
        return recentViolations >= configService.getAbuseThreshold(category);
    }
}

// Token bucket implementation
public class TokenBucket {
    
    private final int capacity;
    private final double refillRate;
    private double tokens;
    private long lastRefillTimestamp;
    
    public TokenBucket(int capacity, double refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }
    
    public synchronized boolean tryConsume() {
        refill();
        
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        
        return false;
    }
    
    private void refill() {
        long now = System.currentTimeMillis();
        double elapsedSeconds = (now - lastRefillTimestamp) / 1000.0;
        double tokensToAdd = elapsedSeconds * refillRate;
        
        if (tokensToAdd > 0) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}

// Rate limit categories
public enum RateLimitCategory {
    LOGIN_ATTEMPTS,
    COMMAND_EXECUTION,
    API_REQUESTS,
    SERVER_OPERATIONS,
    RESOURCE_INTENSIVE_OPERATIONS
}
```

## Configuration Options

### Security Configuration Structure

The security settings are structured in a hierarchical configuration:

```yaml
security:
  authentication:
    # Authentication Settings
    require_2fa_for_admins: true
    session_timeout_minutes: 30
    max_failed_attempts: 5
    lockout_duration_minutes: 15
    
  authorization:
    # Role definitions
    roles:
      admin:
        permissions:
          - "mcp.*"
      moderator:
        permissions:
          - "mcp.command.*"
          - "-mcp.command.config.*"
      user:
        permissions:
          - "mcp.command.info.*"
          - "mcp.command.status.*"
    
  command_security:
    # Command whitelist
    whitelist_enabled: true
    global_whitelist:
      - "status"
      - "info"
      - "help"
    server_specific_whitelist:
      survival:
        - "backup"
        - "restart"
      creative:
        - "gamemode"
        - "give"
    
  audit_logging:
    # Logging configuration
    enabled: true
    log_level: INFO
    storage_type: FILE
    file_options:
      path: "./logs/mcp-audit.log"
      max_size_mb: 10
      max_history: 7
    alert_events:
      - "SECURITY_CONFIG_CHANGE"
      - "FAILED_LOGIN"
      - "PERMISSION_DENIED"
    
  rate_limiting:
    # Rate limiting configuration
    enabled: true
    categories:
      LOGIN_ATTEMPTS:
        capacity: 5
        refill_rate: 5  # tokens per minute
        abuse_threshold: 10
      COMMAND_EXECUTION:
        capacity: 30
        refill_rate: 20
        abuse_threshold: 50
      API_REQUESTS:
        capacity: 100
        refill_rate: 60
        abuse_threshold: 200
      
    role_multipliers:
      admin: 2.0
      moderator: 1.5
      user: 1.0
      
  ip_security:
    # IP security settings
    whitelist_enabled: false
    whitelist:
      - "127.0.0.1"
      - "192.168.1.0/24"
    auto_block_suspicious: true
    max_sessions_per_ip: 3
```

### Configuration Loading

```java
// Configuration service
public class SecurityConfigurationService {
    
    private SecurityConfig config;
    private final Path configPath;
    
    public SecurityConfigurationService(Path configPath) {
        this.configPath = configPath;
        loadConfiguration();
    }
    
    public void loadConfiguration() {
        try {
            // Load configuration from file
            if (Files.exists(configPath)) {
                config = YamlParser.parse(Files.newInputStream(configPath), SecurityConfig.class);
            } else {
                config = createDefaultConfiguration();
                saveConfiguration();
            }
        } catch (Exception e) {
            logger.error("Failed to load security configuration", e);
            config = createDefaultConfiguration();
        }
    }
    
    public void saveConfiguration() {
        try {
            Files.createDirectories(configPath.getParent());
            YamlParser.write(Files.newOutputStream(configPath), config);
        } catch (Exception e) {
            logger.error("Failed to save security configuration", e);
        }
    }
    
    private SecurityConfig createDefaultConfiguration() {
        // Create default security configuration with secure defaults
        SecurityConfig defaultConfig = new SecurityConfig();
        
        // Set authentication defaults
        defaultConfig.getAuthentication().setRequire2faForAdmins(true);
        defaultConfig.getAuthentication().setSessionTimeoutMinutes(30);
        defaultConfig.getAuthentication().setMaxFailedAttempts(5);
        defaultConfig.getAuthentication().setLockoutDurationMinutes(15);
        
        // Set other defaults...
        
        return defaultConfig;
    }
    
    // Getters for specific configuration values
    public int getSessionTimeout() {
        return config.getAuthentication().getSessionTimeoutMinutes();
    }
    
    public boolean isCommandWhitelisted(String command) {
        return !config.getCommandSecurity().isWhitelistEnabled() || 
               config.getCommandSecurity().getGlobalWhitelist().contains(command);
    }
    
    public int getRateLimitCapacity(String role, RateLimitCategory category) {
        double multiplier = config.getRateLimiting().getRoleMultipliers().getOrDefault(role, 1.0);
        int baseCapacity = config.getRateLimiting().getCategories().get(category).getCapacity();
        return (int)(baseCapacity * multiplier);
    }
    
    // Other configuration getters...
}
```

## Implementation Patterns & Examples

### Security Service Integration

```java
// Main security manager
public class McpSecurityManager {
    
    private final AuthenticationService authService;
    private final AuthorizationService authzService;
    private final RateLimiterService rateLimiter;
    private final AuditLogger auditLogger;
    private final SecurityConfigurationService configService;
    
    public SecurityContext authenticate(String username, String password, String totpCode, String ipAddress) {
        // Check rate limits first
        if (!rateLimiter.checkRateLimit(username, RateLimitCategory.LOGIN_ATTEMPTS)) {
            auditLogger.log(AuditEvent.RATE_LIMIT_EXCEEDED, username, "LOGIN_ATTEMPTS");
            return null;
        }
        
        // Check IP restrictions
        if (!isIpAllowed(ipAddress)) {
            auditLogger.log(AuditEvent.IP_BLOCKED, username, ipAddress);
            return null;
        }
        
        // Perform authentication
        AuthenticationResult result = authService.authenticate(username, password, totpCode);
        
        if (result.isSuccess()) {
            // Create security context
            SecurityContext context = new SecurityContext(
                result.getUser(),
                result.getSessionToken(),
                ipAddress
            );
            
            return context;
        }
        
        return null;
    }
    
    public boolean authorizeCommand(SecurityContext context, String command, Map<String, Object> params) {
        // Check if context is valid
        if (!isContextValid(context)) {
            auditLogger.log(AuditEvent.INVALID_SECURITY_CONTEXT, context.getUser().getUsername());
            return false;
        }
        
        // Check rate limits
        if (!rateLimiter.checkRateLimit(context.getUser().getUsername(), RateLimitCategory.COMMAND_EXECUTION)) {
            auditLogger.log(AuditEvent.RATE_LIMIT_EXCEEDED, context.getUser().getUsername(), "COMMAND_EXECUTION");
            return false;
        }
        
        // Check if command is whitelisted
        if (!configService.isCommandWhitelisted(command)) {
            auditLogger.log(AuditEvent.BLOCKED_COMMAND, context.getUser().getUsername(), command);
            return false;
        }
        
        // Check if user has permission
        String permission = "mcp.command." + command;
        if (!authzService.hasPermission(context.getUser(), permission)) {
            auditLogger.log(AuditEvent.PERMISSION_DENIED, context.getUser().getUsername(), permission);
            return false;
        }
        
        // Command is authorized
        return true;
    }
    
    private boolean isContextValid(SecurityContext context) {
        // Check if session is valid and not expired
        return authService.isSessionValid(context.getSessionToken());
    }
    
    private boolean isIpAllowed(String ipAddress) {
        // Check IP whitelist if enabled
        if (configService.isIpWhitelistEnabled()) {
            return configService.isIpWhitelisted(ipAddress);
        }
        
        // Check if IP is in blocklist
        return !configService.isIpBlocked(ipAddress);
    }
}
```

### Plugin Integration

```java
public class MinecraftMcpPlugin extends JavaPlugin {
    
    private McpSecurityManager securityManager;
    private CommandRegistry commandRegistry;
    private AuditLogger auditLogger;
    private SecurityConfigurationService configService;
    
    @Override
    public void onEnable() {
        // Initialize configuration
        configService = new SecurityConfigurationService(
            getDataFolder().toPath().resolve("security-config.yml"));
        
        // Initialize security components
        initializeSecurityComponents();
        
        // Register commands
        registerCommands();
        
        // Register event listeners
        registerEventListeners();
        
        getLogger().info("MCP Security Framework enabled");
    }
    
    private void initializeSecurityComponents() {
        // Initialize audit logger
        auditLogger = new AuditLogger(
            new FileLogStorage(getDataFolder().toPath().resolve("logs")));
        
        // Initialize user repository (database connection or file-based)
        UserRepository userRepository = new FileUserRepository(
            getDataFolder().toPath().resolve("users.json"));
        
        // Initialize role repository
        RoleRepository roleRepository = new ConfigBackedRoleRepository(configService);
        
        // Initialize other components
        AuthenticationService authService = new McpAuthenticationService(userRepository, auditLogger);
        AuthorizationService authzService = new McpAuthorizationService(roleRepository, auditLogger);
        RateLimiterService rateLimiter = new RateLimiterService(configService);
        
        // Create security manager
        securityManager = new McpSecurityManager(
            authService, 
            authzService, 
            rateLimiter, 
            auditLogger, 
            configService);
        
        // Create command registry
        commandRegistry = new CommandRegistry(authzService, auditLogger);
    }
    
    private void registerCommands() {
        // Register MCP commands with security metadata
        commandRegistry.registerCommand("status", new StatusCommandHandler(), "mcp.command.status");
        commandRegistry.registerCommand("restart", new RestartCommandHandler(), "mcp.command.server.restart");
        commandRegistry.registerCommand("backup", new BackupCommandHandler(), "mcp.command.server.backup");
        // Register more commands...
        
        // Register Bukkit command executor
        getCommand("mcp").setExecutor(new McpCommandExecutor(commandRegistry, securityManager));
    }
    
    private void registerEventListeners() {
        // Register login event listener for authentication
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(securityManager), this);
        
        // Register other listeners
        getServer().getPluginManager().registerEvents(new SecurityEventListener(securityManager, auditLogger), this);
    }
    
    @Override
    public void onDisable() {
        // Log shutdown
        auditLogger.log(AuditEvent.SERVER_STOP, "CONSOLE");
        
        // Cleanup resources
        auditLogger.shutdown();
        
        getLogger().info("MCP Security Framework disabled");
    }
}
```

## Security Testing & Validation

### Security Testing Approaches

1. **Unit Testing**:
   - Test individual security components
   - Validate permission checks
   - Verify rate limiting behavior
   - Test audit logging output

2. **Integration Testing**:
   - End-to-end authentication flows
   - Command authorization pipelines
   - Configuration loading and validation

3. **Security-Focused Testing**:
   - Authentication bypass attempts
   - Permission escalation tests
   - Rate limiting effectiveness
   - Audit log completeness

### Example Test Cases

```java
// Unit test for authentication service
@Test
public void testAuthenticationFailure() {
    // Arrange
    UserRepository mockRepo = mock(UserRepository.class);
    AuditLogger mockLogger = mock(AuditLogger.class);
    
    User testUser = new User("testuser", "$2a$10$...")
    when(mockRepo.findByUsername("testuser")).thenReturn(testUser);
    
    McpAuthenticationService authService = new McpAuthenticationService(mockRepo, mockLogger);
    
    // Act
    AuthenticationResult result = authService.authenticate("testuser", "wrongpassword", null);
    
    // Assert
    assertFalse(result.isSuccess());
    verify(mockLogger).log(eq(AuditEvent.FAILED_LOGIN), eq("testuser"));
}

// Integration test for command authorization
@Test
public void testCommandAuthorizationWithInsufficientPermissions() {
    // Arrange
    SecurityContext context = new SecurityContext(
        new User("testuser", Collections.singleton(new Role("user"))),
        "session-token-123",
        "127.0.0.1"
    );
    
    Map<String, Object> params = new HashMap<>();
    
    // Act
    boolean authorized = securityManager.authorizeCommand(context, "restart", params);
    
    // Assert
    assertFalse(authorized);
    verify(auditLogger).log(
        eq(AuditEvent.PERMISSION_DENIED), 
        eq("testuser"), 
        eq("mcp.command.restart")
    );
}
```

## Conclusion

This security framework provides a robust, layered approach to securing Minecraft MCP plugin functionality. By implementing proper authentication, authorization, command security, logging, and rate limiting, server administrators can safely expose powerful functionality while maintaining security controls and visibility into system usage.

The modular design allows for customization and extension to meet specific requirements, while maintaining a secure-by-default posture. Proper configuration and ongoing security testing are essential to maintain the effectiveness of these controls.
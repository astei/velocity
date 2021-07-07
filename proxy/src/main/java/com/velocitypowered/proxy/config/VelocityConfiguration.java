/*
 * Copyright (C) 2018 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.Expose;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.proxy.util.AddressUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.legacytext3.LegacyText3ComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class VelocityConfiguration implements ProxyConfig {

  private static final Logger logger = LogManager.getLogger(VelocityConfiguration.class);

  @Expose private String bind = "0.0.0.0:25577";
  @Expose private String motd = "&3A Velocity Server";
  @Expose private int showMaxPlayers = 500;
  @Expose private boolean onlineMode = true;
  @Expose private boolean preventClientProxyConnections = false;
  @Expose private PlayerInfoForwarding playerInfoForwardingMode = PlayerInfoForwarding.NONE;
  private byte[] forwardingSecret = generateRandomString(12).getBytes(StandardCharsets.UTF_8);
  @Expose private boolean announceForge = false;
  @Expose private boolean onlineModeKickExistingPlayers = false;
  @Expose private PingPassthroughMode pingPassthrough = PingPassthroughMode.DISABLED;
  private final Servers servers;
  private final ForcedHosts forcedHosts;
  @Expose private final Advanced advanced;
  @Expose private final Query query;
  private final Metrics metrics;
  private final Messages messages;
  private net.kyori.adventure.text.@MonotonicNonNull Component motdAsComponent;
  private @Nullable Favicon favicon;

  private VelocityConfiguration(Servers servers, ForcedHosts forcedHosts, Advanced advanced,
      Query query, Metrics metrics, Messages messages) {
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
    this.messages = messages;
  }

  private VelocityConfiguration(String bind, String motd, int showMaxPlayers, boolean onlineMode,
      boolean preventClientProxyConnections, boolean announceForge,
      PlayerInfoForwarding playerInfoForwardingMode, byte[] forwardingSecret,
      boolean onlineModeKickExistingPlayers, PingPassthroughMode pingPassthrough, Servers servers,
      ForcedHosts forcedHosts, Advanced advanced, Query query, Metrics metrics, Messages messages) {
    this.bind = bind;
    this.motd = motd;
    this.showMaxPlayers = showMaxPlayers;
    this.onlineMode = onlineMode;
    this.preventClientProxyConnections = preventClientProxyConnections;
    this.announceForge = announceForge;
    this.playerInfoForwardingMode = playerInfoForwardingMode;
    this.forwardingSecret = forwardingSecret;
    this.onlineModeKickExistingPlayers = onlineModeKickExistingPlayers;
    this.pingPassthrough = pingPassthrough;
    this.servers = servers;
    this.forcedHosts = forcedHosts;
    this.advanced = advanced;
    this.query = query;
    this.metrics = metrics;
    this.messages = messages;
  }

  /**
   * Attempts to validate the configuration.
   * @return {@code true} if the configuration is sound, {@code false} if not
   */
  public boolean validate() {
    boolean valid = true;

    if (bind.isEmpty()) {
      logger.error("'bind' option is empty.");
      valid = false;
    } else {
      try {
        AddressUtil.parseAddress(bind);
      } catch (IllegalArgumentException e) {
        logger.error("'bind' option does not specify a valid IP address.", e);
        valid = false;
      }
    }

    if (!onlineMode) {
      logger.warn("The proxy is running in offline mode! This is a security risk and you will NOT "
          + "receive any support!");
    }

    switch (playerInfoForwardingMode) {
      case NONE:
        logger.warn("Player info forwarding is disabled! All players will appear to be connecting "
            + "from the proxy and will have offline-mode UUIDs.");
        break;
      case MODERN:
      case BUNGEEGUARD:
        if (forwardingSecret == null || forwardingSecret.length == 0) {
          logger.error("You don't have a forwarding secret set. This is required for security.");
          valid = false;
        }
        break;
      default:
        break;
    }

    if (servers.getServers().isEmpty()) {
      logger.warn("You don't have any servers configured.");
    }

    for (Map.Entry<String, String> entry : servers.getServers().entrySet()) {
      try {
        AddressUtil.parseAddress(entry.getValue());
      } catch (IllegalArgumentException e) {
        logger.error("Server {} does not have a valid IP address.", entry.getKey(), e);
        valid = false;
      }
    }

    for (String s : servers.getAttemptConnectionOrder()) {
      if (!servers.getServers().containsKey(s)) {
        logger.error("Fallback server " + s + " is not registered in your configuration!");
        valid = false;
      }
    }

    for (Map.Entry<String, List<String>> entry : forcedHosts.getForcedHosts().entrySet()) {
      if (entry.getValue().isEmpty()) {
        logger.error("Forced host '{}' does not contain any servers", entry.getKey());
        valid = false;
        continue;
      }

      for (String server : entry.getValue()) {
        if (!servers.getServers().containsKey(server)) {
          logger.error("Server '{}' for forced host '{}' does not exist", server, entry.getKey());
          valid = false;
        }
      }
    }

    try {
      getMotd();
    } catch (Exception e) {
      logger.error("Can't parse your MOTD", e);
      valid = false;
    }

    if (advanced.compressionLevel < -1 || advanced.compressionLevel > 9) {
      logger.error("Invalid compression level {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionLevel == 0) {
      logger.warn("ALL packets going through the proxy will be uncompressed. This will increase "
          + "bandwidth usage.");
    }

    if (advanced.compressionThreshold < -1) {
      logger.error("Invalid compression threshold {}", advanced.compressionLevel);
      valid = false;
    } else if (advanced.compressionThreshold == 0) {
      logger.warn("ALL packets going through the proxy will be compressed. This will compromise "
          + "throughput and increase CPU usage!");
    }

    if (advanced.loginRatelimit < 0) {
      logger.error("Invalid login ratelimit {}ms", advanced.loginRatelimit);
      valid = false;
    }

    loadFavicon();

    return valid;
  }

  private void loadFavicon() {
    Path faviconPath = Paths.get("server-icon.png");
    if (Files.exists(faviconPath)) {
      try {
        this.favicon = Favicon.create(faviconPath);
      } catch (Exception e) {
        logger.info("Unable to load your server-icon.png, continuing without it.", e);
      }
    }
  }

  public InetSocketAddress getBind() {
    return AddressUtil.parseAndResolveAddress(bind);
  }

  @Override
  public boolean isQueryEnabled() {
    return query.isQueryEnabled();
  }

  @Override
  public int getQueryPort() {
    return query.getQueryPort();
  }

  @Override
  public String getQueryMap() {
    return query.getQueryMap();
  }

  @Override
  public boolean shouldQueryShowPlugins() {
    return query.shouldQueryShowPlugins();
  }

  /**
   * Returns the proxy's MOTD.
   *
   * @return the MOTD
   */
  @Override
  public net.kyori.text.Component getMotdComponent() {
    return LegacyText3ComponentSerializer.get().serialize(this.getMotd());
  }

  @Override
  public net.kyori.adventure.text.Component getMotd() {
    if (motdAsComponent == null) {
      if (motd.startsWith("{")) {
        motdAsComponent = GsonComponentSerializer.gson().deserialize(motd);
      } else {
        motdAsComponent = LegacyComponentSerializer.legacy('&').deserialize(motd);
      }
    }
    return motdAsComponent;
  }

  @Override
  public int getShowMaxPlayers() {
    return showMaxPlayers;
  }

  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  @Override
  public boolean shouldPreventClientProxyConnections() {
    return preventClientProxyConnections;
  }

  public PlayerInfoForwarding getPlayerInfoForwardingMode() {
    return playerInfoForwardingMode;
  }

  public byte[] getForwardingSecret() {
    return forwardingSecret.clone();
  }

  @Override
  public Map<String, String> getServers() {
    return servers.getServers();
  }

  @Override
  public List<String> getAttemptConnectionOrder() {
    return servers.getAttemptConnectionOrder();
  }

  @Override
  public Map<String, List<String>> getForcedHosts() {
    return forcedHosts.getForcedHosts();
  }

  @Override
  public int getCompressionThreshold() {
    return advanced.getCompressionThreshold();
  }

  @Override
  public int getCompressionLevel() {
    return advanced.getCompressionLevel();
  }

  @Override
  public int getLoginRatelimit() {
    return advanced.getLoginRatelimit();
  }

  @Override
  public Optional<Favicon> getFavicon() {
    return Optional.ofNullable(favicon);
  }

  @Override
  public boolean isAnnounceForge() {
    return announceForge;
  }

  @Override
  public int getConnectTimeout() {
    return advanced.getConnectionTimeout();
  }

  @Override
  public int getReadTimeout() {
    return advanced.getReadTimeout();
  }

  public boolean isProxyProtocol() {
    return advanced.isProxyProtocol();
  }

  public boolean useTcpFastOpen() {
    return advanced.tcpFastOpen;
  }

  public Metrics getMetrics() {
    return metrics;
  }

  public PingPassthroughMode getPingPassthrough() {
    return pingPassthrough;
  }

  public boolean isBungeePluginChannelEnabled() {
    return advanced.isBungeePluginMessageChannel();
  }

  public boolean isShowPingRequests() {
    return advanced.isShowPingRequests();
  }

  public boolean isFailoverOnUnexpectedServerDisconnect() {
    return advanced.isFailoverOnUnexpectedServerDisconnect();
  }

  public boolean isAnnounceProxyCommands() {
    return advanced.isAnnounceProxyCommands();
  }

  public boolean isLogCommandExecutions() {
    return advanced.isLogCommandExecutions();
  }

  public boolean isLogServerConnections() {
    return advanced.isLogServerConnections();
  }

  public Messages getMessages() {
    return messages;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("bind", bind)
        .add("motd", motd)
        .add("showMaxPlayers", showMaxPlayers)
        .add("onlineMode", onlineMode)
        .add("playerInfoForwardingMode", playerInfoForwardingMode)
        .add("forwardingSecret", forwardingSecret)
        .add("announceForge", announceForge)
        .add("servers", servers)
        .add("forcedHosts", forcedHosts)
        .add("advanced", advanced)
        .add("query", query)
        .add("favicon", favicon)
        .toString();
  }

  /**
   * Reads the Velocity configuration from {@code path}.
   * @param path the path to read from
   * @return the deserialized Velocity configuration
   * @throws IOException if we could not read from the {@code path}.
   */
  @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
      justification = "I looked carefully and there's no way SpotBugs is right.")
  public static VelocityConfiguration read(Path path) throws IOException {
    URL defaultConfigLocation = VelocityConfiguration.class.getClassLoader()
        .getResource("default-velocity.toml");
    if (defaultConfigLocation == null) {
      throw new RuntimeException("Default configuration file does not exist.");
    }

    boolean mustResave = false;
    CommentedFileConfig config = CommentedFileConfig.builder(path)
        .defaultData(defaultConfigLocation)
        .autosave()
        .preserveInsertionOrder()
        .sync()
        .build();
    config.load();

    // Create temporary default configuration
    File tmpFile = File.createTempFile("default-config", null);
    tmpFile.deleteOnExit();

    // Copy over default file to tmp location
    try (InputStream in = defaultConfigLocation.openStream()) {
      Files.copy(in, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    CommentedFileConfig defaultConfig = CommentedFileConfig.of(tmpFile, TomlFormat.instance());
    defaultConfig.load();

    // Handle any cases where the config needs to be saved again
    byte[] forwardingSecret;
    String forwardingSecretString = config.get("forwarding-secret");
    if (forwardingSecretString == null || forwardingSecretString.isEmpty()) {
      forwardingSecretString = generateRandomString(12);
      config.set("forwarding-secret", forwardingSecretString);
      mustResave = true;
    }
    forwardingSecret = forwardingSecretString.getBytes(StandardCharsets.UTF_8);

    if (mustResave) {
      config.save();
    }

    // Read the rest of the config
    CommentedConfig serversConfig = config.get("servers");
    CommentedConfig forcedHostsConfig = config.get("forced-hosts");
    CommentedConfig advancedConfig = config.get("advanced");
    CommentedConfig queryConfig = config.get("query");
    CommentedConfig metricsConfig = config.get("metrics");
    CommentedConfig messagesConfig = config.get("messages");
    PlayerInfoForwarding forwardingMode = config.getEnumOrElse("player-info-forwarding-mode",
        PlayerInfoForwarding.NONE);
    PingPassthroughMode pingPassthroughMode = config.getEnumOrElse("ping-passthrough",
        PingPassthroughMode.DISABLED);

    String bind = config.getOrElse("bind", "0.0.0.0:25577");
    String motd = config.getOrElse("motd", "&#09add3A Velocity Server");
    int maxPlayers = config.getIntOrElse("show-max-players", 500);
    Boolean onlineMode = config.getOrElse("online-mode", true);
    Boolean announceForge = config.getOrElse("announce-forge", true);
    Boolean preventClientProxyConnections = config.getOrElse("prevent-client-proxy-connections",
        true);
    Boolean kickExisting = config.getOrElse("kick-existing-players", false);

    return new VelocityConfiguration(
        bind,
        motd,
        maxPlayers,
        onlineMode,
        preventClientProxyConnections,
        announceForge,
        forwardingMode,
        forwardingSecret,
        kickExisting,
        pingPassthroughMode,
        new Servers(serversConfig),
        new ForcedHosts(forcedHostsConfig),
        new Advanced(advancedConfig),
        new Query(queryConfig),
        new Metrics(metricsConfig),
        new Messages(messagesConfig, defaultConfig.get("messages"))
    );
  }

  private static String generateRandomString(int length) {
    String chars = "AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz1234567890";
    StringBuilder builder = new StringBuilder();
    Random rnd = new SecureRandom();
    for (int i = 0; i < length; i++) {
      builder.append(chars.charAt(rnd.nextInt(chars.length())));
    }
    return builder.toString();
  }

  public boolean isOnlineModeKickExistingPlayers() {
    return onlineModeKickExistingPlayers;
  }

  private static class Servers {

    private Map<String, String> servers = ImmutableMap.of(
        "lobby", "127.0.0.1:30066",
        "factions", "127.0.0.1:30067",
        "minigames", "127.0.0.1:30068"
    );
    private List<String> attemptConnectionOrder = ImmutableList.of("lobby");

    private Servers() {
    }

    private Servers(CommentedConfig config) {
      if (config != null) {
        Map<String, String> servers = new HashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
          if (entry.getValue() instanceof String) {
            servers.put(cleanServerName(entry.getKey()), entry.getValue());
          } else {
            if (!entry.getKey().equalsIgnoreCase("try")) {
              throw new IllegalArgumentException(
                  "Server entry " + entry.getKey() + " is not a string!");
            }
          }
        }
        this.servers = ImmutableMap.copyOf(servers);
        this.attemptConnectionOrder = config.getOrElse("try", attemptConnectionOrder);
      }
    }

    private Servers(Map<String, String> servers, List<String> attemptConnectionOrder) {
      this.servers = servers;
      this.attemptConnectionOrder = attemptConnectionOrder;
    }

    private Map<String, String> getServers() {
      return servers;
    }

    public void setServers(Map<String, String> servers) {
      this.servers = servers;
    }

    public List<String> getAttemptConnectionOrder() {
      return attemptConnectionOrder;
    }

    public void setAttemptConnectionOrder(List<String> attemptConnectionOrder) {
      this.attemptConnectionOrder = attemptConnectionOrder;
    }

    /**
     * TOML requires keys to match a regex of {@code [A-Za-z0-9_-]} unless it is wrapped in
     * quotes; however, the TOML parser returns the key with the quotes so we need to clean the
     * server name before we pass it onto server registration to keep proper server name behavior.
     *
     * @param name the server name to clean
     *
     * @return the cleaned server name
     */
    private String cleanServerName(String name) {
      return name.replace("\"", "");
    }

    @Override
    public String toString() {
      return "Servers{"
          + "servers=" + servers
          + ", attemptConnectionOrder=" + attemptConnectionOrder
          + '}';
    }
  }

  private static class ForcedHosts {

    private Map<String, List<String>> forcedHosts = ImmutableMap.of(
        "lobby.example.com", ImmutableList.of("lobby"),
        "factions.example.com", ImmutableList.of("factions"),
        "minigames.example.com", ImmutableList.of("minigames")
    );

    private ForcedHosts() {
    }

    private ForcedHosts(CommentedConfig config) {
      if (config != null) {
        Map<String, List<String>> forcedHosts = new HashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
          if (entry.getValue() instanceof String) {
            forcedHosts.put(entry.getKey().toLowerCase(Locale.ROOT),
                ImmutableList.of(entry.getValue()));
          } else if (entry.getValue() instanceof List) {
            forcedHosts.put(entry.getKey().toLowerCase(Locale.ROOT),
                ImmutableList.copyOf((List<String>) entry.getValue()));
          } else {
            throw new IllegalStateException(
                "Invalid value of type " + entry.getValue().getClass() + " in forced hosts!");
          }
        }
        this.forcedHosts = ImmutableMap.copyOf(forcedHosts);
      }
    }

    private ForcedHosts(Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    private Map<String, List<String>> getForcedHosts() {
      return forcedHosts;
    }

    private void setForcedHosts(Map<String, List<String>> forcedHosts) {
      this.forcedHosts = forcedHosts;
    }

    @Override
    public String toString() {
      return "ForcedHosts{"
          + "forcedHosts=" + forcedHosts
          + '}';
    }
  }

  private static class Advanced {

    @Expose private int compressionThreshold = 256;
    @Expose private int compressionLevel = -1;
    @Expose private int loginRatelimit = 3000;
    @Expose private int connectionTimeout = 5000;
    @Expose private int readTimeout = 30000;
    @Expose private boolean proxyProtocol = false;
    @Expose private boolean tcpFastOpen = false;
    @Expose private boolean bungeePluginMessageChannel = true;
    @Expose private boolean showPingRequests = false;
    @Expose private boolean failoverOnUnexpectedServerDisconnect = true;
    @Expose private boolean announceProxyCommands = true;
    @Expose private boolean logCommandExecutions = false;
    @Expose private boolean logServerConnections = true;

    private Advanced() {
    }

    private Advanced(CommentedConfig config) {
      if (config != null) {
        this.compressionThreshold = config.getIntOrElse("compression-threshold", 256);
        this.compressionLevel = config.getIntOrElse("compression-level", -1);
        this.loginRatelimit = config.getIntOrElse("login-ratelimit", 3000);
        this.connectionTimeout = config.getIntOrElse("connection-timeout", 5000);
        this.readTimeout = config.getIntOrElse("read-timeout", 30000);
        if (config.contains("haproxy-protocol")) {
          this.proxyProtocol = config.getOrElse("haproxy-protocol", false);
        } else {
          this.proxyProtocol = config.getOrElse("proxy-protocol", false);
        }
        this.tcpFastOpen = config.getOrElse("tcp-fast-open", false);
        this.bungeePluginMessageChannel = config.getOrElse("bungee-plugin-message-channel", true);
        this.showPingRequests = config.getOrElse("show-ping-requests", false);
        this.failoverOnUnexpectedServerDisconnect = config
            .getOrElse("failover-on-unexpected-server-disconnect", true);
        this.announceProxyCommands = config.getOrElse("announce-proxy-commands", true);
        this.logCommandExecutions = config.getOrElse("log-command-executions", false);
        this.logServerConnections = config.getOrElse("log-server-connections", true);
      }
    }

    public int getCompressionThreshold() {
      return compressionThreshold;
    }

    public int getCompressionLevel() {
      return compressionLevel;
    }

    public int getLoginRatelimit() {
      return loginRatelimit;
    }

    public int getConnectionTimeout() {
      return connectionTimeout;
    }

    public int getReadTimeout() {
      return readTimeout;
    }

    public boolean isProxyProtocol() {
      return proxyProtocol;
    }

    public boolean isTcpFastOpen() {
      return tcpFastOpen;
    }

    public boolean isBungeePluginMessageChannel() {
      return bungeePluginMessageChannel;
    }

    public boolean isShowPingRequests() {
      return showPingRequests;
    }

    public boolean isFailoverOnUnexpectedServerDisconnect() {
      return failoverOnUnexpectedServerDisconnect;
    }

    public boolean isAnnounceProxyCommands() {
      return announceProxyCommands;
    }

    public boolean isLogCommandExecutions() {
      return logCommandExecutions;
    }

    public boolean isLogServerConnections() {
      return logServerConnections;
    }

    @Override
    public String toString() {
      return "Advanced{"
          + "compressionThreshold=" + compressionThreshold
          + ", compressionLevel=" + compressionLevel
          + ", loginRatelimit=" + loginRatelimit
          + ", connectionTimeout=" + connectionTimeout
          + ", readTimeout=" + readTimeout
          + ", proxyProtocol=" + proxyProtocol
          + ", tcpFastOpen=" + tcpFastOpen
          + ", bungeePluginMessageChannel=" + bungeePluginMessageChannel
          + ", showPingRequests=" + showPingRequests
          + ", failoverOnUnexpectedServerDisconnect=" + failoverOnUnexpectedServerDisconnect
          + ", announceProxyCommands=" + announceProxyCommands
          + ", logCommandExecutions=" + logCommandExecutions
          + ", logServerConnections=" + logServerConnections
          + '}';
    }
  }

  private static class Query {

    @Expose private boolean queryEnabled = false;
    @Expose private int queryPort = 25577;
    @Expose private String queryMap = "Velocity";
    @Expose private boolean showPlugins = false;

    private Query() {
    }

    private Query(boolean queryEnabled, int queryPort, String queryMap, boolean showPlugins) {
      this.queryEnabled = queryEnabled;
      this.queryPort = queryPort;
      this.queryMap = queryMap;
      this.showPlugins = showPlugins;
    }

    private Query(CommentedConfig config) {
      if (config != null) {
        this.queryEnabled = config.getOrElse("enabled", false);
        this.queryPort = config.getIntOrElse("port", 25577);
        this.queryMap = config.getOrElse("map", "Velocity");
        this.showPlugins = config.getOrElse("show-plugins", false);
      }
    }

    public boolean isQueryEnabled() {
      return queryEnabled;
    }

    public int getQueryPort() {
      return queryPort;
    }

    public String getQueryMap() {
      return queryMap;
    }

    public boolean shouldQueryShowPlugins() {
      return showPlugins;
    }

    @Override
    public String toString() {
      return "Query{"
          + "queryEnabled=" + queryEnabled
          + ", queryPort=" + queryPort
          + ", queryMap='" + queryMap + '\''
          + ", showPlugins=" + showPlugins
          + '}';
    }
  }

  public static class Metrics {
    private boolean enabled = true;

    private Metrics(CommentedConfig toml) {
      if (toml != null) {
        this.enabled = toml.getOrElse("enabled", true);
      }
    }

    public boolean isEnabled() {
      return enabled;
    }
  }

  public static class Messages {

    private final CommentedConfig toml;
    private final CommentedConfig defaultToml;

    private final String kickPrefix;
    private final String disconnectPrefix;
    private final String onlineModeOnly;
    private final String noAvailableServers;
    private final String alreadyConnected;
    private final String movedToNewServerPrefix;
    private final String genericConnectionError;

    private Messages(CommentedConfig toml, CommentedConfig defaultToml) {
      this.toml = toml;
      this.defaultToml = defaultToml;
      this.kickPrefix = getString("kick-prefix");
      this.disconnectPrefix = getString("disconnect-prefix");
      this.onlineModeOnly = getString("online-mode-only");
      this.noAvailableServers = getString("no-available-servers");
      this.alreadyConnected = getString("already-connected");
      this.movedToNewServerPrefix = getString("moved-to-new-server-prefix");
      this.genericConnectionError = getString("generic-connection-error");
    }

    private String getString(String path) {
      String def = defaultToml.getOrElse(path, "");
      if (toml == null) {
        return def;
      }
      return toml.getOrElse(path, def);
    }

    public Component getKickPrefix(String server) {
      return deserialize(String.format(kickPrefix, server));
    }

    public Component getDisconnectPrefix(String server) {
      return deserialize(String.format(disconnectPrefix, server));
    }

    public Component getOnlineModeOnly() {
      return deserialize(onlineModeOnly);
    }

    public Component getNoAvailableServers() {
      return deserialize(noAvailableServers);
    }

    public Component getAlreadyConnected() {
      return deserialize(alreadyConnected);
    }

    public Component getMovedToNewServerPrefix() {
      return deserialize(movedToNewServerPrefix);
    }

    public Component getGenericConnectionError() {
      return deserialize(genericConnectionError);
    }

    private Component deserialize(String str) {
      if (str.startsWith("{")) {
        return GsonComponentSerializer.gson().deserialize(str);
      }
      return LegacyComponentSerializer.legacyAmpersand().deserialize(str);
    }
  }
}

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

package com.velocitypowered.proxy.connection.client;

import static com.velocitypowered.api.proxy.ConnectionRequestBuilder.Status.ALREADY_CONNECTED;
import static com.velocitypowered.proxy.connection.util.ConnectionRequestResults.plainResult;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent.DisconnectPlayer;
import com.velocitypowered.api.event.player.KickedFromServerEvent.Notify;
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer;
import com.velocitypowered.api.event.player.KickedFromServerEvent.ServerKickResult;
import com.velocitypowered.api.event.player.PlayerModInfoEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.ModInfo;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.MinecraftConnectionAssociation;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.player.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.util.ConnectionMessages;
import com.velocitypowered.proxy.connection.util.ConnectionRequestResults.Impl;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.Chat;
import com.velocitypowered.proxy.protocol.packet.ClientSettings;
import com.velocitypowered.proxy.protocol.packet.Disconnect;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooter;
import com.velocitypowered.proxy.protocol.packet.KeepAlive;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import com.velocitypowered.proxy.protocol.packet.ResourcePackRequest;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import com.velocitypowered.proxy.server.VelocityRegisteredServer;
import com.velocitypowered.proxy.tablist.VelocityTabList;
import com.velocitypowered.proxy.tablist.VelocityTabListLegacy;
import com.velocitypowered.proxy.util.ClosestLocaleMatcher;
import com.velocitypowered.proxy.util.DurationUtils;
import com.velocitypowered.proxy.util.collect.CappedSet;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

public class ConnectedPlayer implements MinecraftConnectionAssociation, Player {

  private static final int MAX_PLUGIN_CHANNELS = 1024;
  private static final PlainComponentSerializer PASS_THRU_TRANSLATE = new PlainComponentSerializer(
      c -> "", TranslatableComponent::key);
  static final PermissionProvider DEFAULT_PERMISSIONS = s -> PermissionFunction.ALWAYS_UNDEFINED;

  private static final Logger logger = LogManager.getLogger(ConnectedPlayer.class);

  private final Identity identity = new IdentityImpl();
  /**
   * The actual Minecraft connection. This is actually a wrapper object around the Netty channel.
   */
  private final MinecraftConnection connection;
  private final @Nullable InetSocketAddress virtualHost;
  private GameProfile profile;
  private PermissionFunction permissionFunction;
  private int tryIndex = 0;
  private long ping = -1;
  private final boolean onlineMode;
  private @Nullable VelocityServerConnection connectedServer;
  private @Nullable VelocityServerConnection connectionInFlight;
  private @Nullable PlayerSettings settings;
  private @Nullable ModInfo modInfo;
  private Component playerListHeader = Component.empty();
  private Component playerListFooter = Component.empty();
  private final VelocityTabList tabList;
  private final VelocityServer server;
  private ClientConnectionPhase connectionPhase;
  private final Collection<String> knownChannels;
  private final CompletableFuture<Void> teardownFuture = new CompletableFuture<>();
  private @MonotonicNonNull List<String> serversToTry = null;
  private @MonotonicNonNull Boolean previousResourceResponse;
  private final Queue<ResourcePackInfo> outstandingResourcePacks = new ArrayDeque<>();
  private @Nullable ResourcePackInfo pendingResourcePack;
  private @Nullable ResourcePackInfo appliedResourcePack;
  private final @NotNull Pointers pointers = Player.super.pointers().toBuilder()
          .withDynamic(Identity.UUID, this::getUniqueId)
          .withDynamic(Identity.NAME, this::getUsername)
          .withStatic(PermissionChecker.POINTER, getPermissionChecker())
          .build();
  private @Nullable String clientBrand;
  private @Nullable Locale proxyLocale;

  ConnectedPlayer(VelocityServer server, GameProfile profile, MinecraftConnection connection,
      @Nullable InetSocketAddress virtualHost, boolean onlineMode) {
    this.server = server;
    this.profile = profile;
    this.connection = connection;
    this.virtualHost = virtualHost;
    this.permissionFunction = PermissionFunction.ALWAYS_UNDEFINED;
    this.connectionPhase = connection.getType().getInitialClientPhase();
    this.knownChannels = CappedSet.create(MAX_PLUGIN_CHANNELS);
    this.onlineMode = onlineMode;

    if (connection.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      this.tabList = new VelocityTabList(this);
    } else {
      this.tabList = new VelocityTabListLegacy(this);
    }
  }

  @Override
  public @NonNull Identity identity() {
    return this.identity;
  }

  @Override
  public String getUsername() {
    return profile.getName();
  }

  @Override
  public Locale getProxyLocale() {
    if (proxyLocale == null) {
      if (settings == null) {
        return null;
      }
      return settings.getLocale();
    }
    return proxyLocale;
  }

  @Override
  public void setProxyLocale(Locale locale) {
    proxyLocale = locale;
  }

  @Override
  public UUID getUniqueId() {
    return profile.getId();
  }

  @Override
  public Optional<ServerConnection> getCurrentServer() {
    return Optional.ofNullable(connectedServer);
  }

  /**
   * Makes sure the player is connected to a server and returns the server they are connected to.
   * @return the server the player is connected to
   */
  public VelocityServerConnection ensureAndGetCurrentServer() {
    VelocityServerConnection con = this.connectedServer;
    if (con == null) {
      throw new IllegalStateException("Not connected to server!");
    }
    return con;
  }

  @Override
  public GameProfile getGameProfile() {
    return profile;
  }

  public MinecraftConnection getConnection() {
    return connection;
  }

  @Override
  public long getPing() {
    return this.ping;
  }

  void setPing(long ping) {
    this.ping = ping;
  }

  @Override
  public boolean isOnlineMode() {
    return onlineMode;
  }

  @Override
  public PlayerSettings getPlayerSettings() {
    return settings == null ? ClientSettingsWrapper.DEFAULT : this.settings;
  }

  void setPlayerSettings(ClientSettings settings) {
    ClientSettingsWrapper cs = new ClientSettingsWrapper(settings);
    this.settings = cs;
    server.getEventManager().fireAndForget(new PlayerSettingsChangedEvent(this, cs));
  }

  @Override
  public Optional<ModInfo> getModInfo() {
    return Optional.ofNullable(modInfo);
  }

  public void setModInfo(ModInfo modInfo) {
    this.modInfo = modInfo;
    server.getEventManager().fireAndForget(new PlayerModInfoEvent(this, modInfo));
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return (InetSocketAddress) connection.getRemoteAddress();
  }

  @Override
  public Optional<InetSocketAddress> getVirtualHost() {
    return Optional.ofNullable(virtualHost);
  }

  void setPermissionFunction(PermissionFunction permissionFunction) {
    this.permissionFunction = permissionFunction;
  }

  @Override
  public boolean isActive() {
    return connection.getChannel().isActive();
  }

  @Override
  public ProtocolVersion getProtocolVersion() {
    return connection.getProtocolVersion();
  }

  /**
   * Translates the message in the user's locale.
   *
   * @param message the message to translate
   * @return the translated message
   */
  public Component translateMessage(Component message) {
    Locale locale = ClosestLocaleMatcher.INSTANCE
        .lookupClosest(getProxyLocale() == null ? Locale.getDefault() : getProxyLocale());
    return GlobalTranslator.render(message, locale);
  }

  @Override
  public void sendMessage(@NonNull Identity identity, @NonNull Component message) {
    Component translated = translateMessage(message);
    connection.write(Chat.createClientbound(identity, translated, this.getProtocolVersion()));
  }

  @Override
  public void sendMessage(@NonNull Identity identity, @NonNull Component message,
      @NonNull MessageType type) {
    Preconditions.checkNotNull(message, "message");
    Preconditions.checkNotNull(type, "type");

    Component translated = translateMessage(message);
    Chat packet = Chat.createClientbound(identity, translated, this.getProtocolVersion());
    packet.setType(type == MessageType.CHAT ? Chat.CHAT_TYPE : Chat.SYSTEM_TYPE);
    connection.write(packet);
  }

  @Override
  public void sendActionBar(net.kyori.adventure.text.@NonNull Component message) {
    Component translated = translateMessage(message);

    ProtocolVersion playerVersion = getProtocolVersion();
    if (playerVersion.compareTo(ProtocolVersion.MINECRAFT_1_11) >= 0) {
      // Use the title packet instead.
      GenericTitlePacket pkt = GenericTitlePacket.constructTitlePacket(
              GenericTitlePacket.ActionType.SET_ACTION_BAR, playerVersion);
      pkt.setComponent(ProtocolUtils.getJsonChatSerializer(playerVersion)
          .serialize(translated));
      connection.write(pkt);
    } else {
      // Due to issues with action bar packets, we'll need to convert the text message into a
      // legacy message and then inject the legacy text into a component... yuck!
      JsonObject object = new JsonObject();
      object.addProperty("text", LegacyComponentSerializer.legacySection()
          .serialize(translated));
      Chat chat = new Chat();
      chat.setMessage(object.toString());
      chat.setType(Chat.GAME_INFO_TYPE);
      connection.write(chat);
    }
  }

  @Override
  public Component getPlayerListHeader() {
    return this.playerListHeader;
  }

  @Override
  public Component getPlayerListFooter() {
    return this.playerListFooter;
  }

  @Override
  public void sendPlayerListHeader(@NonNull final Component header) {
    this.sendPlayerListHeaderAndFooter(header, this.playerListFooter);
  }

  @Override
  public void sendPlayerListFooter(@NonNull final Component footer) {
    this.sendPlayerListHeaderAndFooter(this.playerListHeader, footer);
  }

  @Override
  public void sendPlayerListHeaderAndFooter(final Component header, final Component footer) {
    Component translatedHeader = translateMessage(header);
    Component translatedFooter = translateMessage(footer);
    this.playerListHeader = translatedHeader;
    this.playerListFooter = translatedFooter;
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      this.connection.write(HeaderAndFooter.create(header, footer, this.getProtocolVersion()));
    }
  }

  @Override
  public void showTitle(net.kyori.adventure.title.@NonNull Title title) {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      GsonComponentSerializer serializer = ProtocolUtils.getJsonChatSerializer(this
          .getProtocolVersion());

      GenericTitlePacket titlePkt = GenericTitlePacket.constructTitlePacket(
                      GenericTitlePacket.ActionType.SET_TITLE, this.getProtocolVersion());
      titlePkt.setComponent(serializer.serialize(translateMessage(title.title())));
      connection.delayedWrite(titlePkt);

      GenericTitlePacket subtitlePkt = GenericTitlePacket.constructTitlePacket(
              GenericTitlePacket.ActionType.SET_SUBTITLE, this.getProtocolVersion());
      subtitlePkt.setComponent(serializer.serialize(translateMessage(title.subtitle())));
      connection.delayedWrite(subtitlePkt);

      GenericTitlePacket timesPkt = GenericTitlePacket.constructTitlePacket(
              GenericTitlePacket.ActionType.SET_TIMES, this.getProtocolVersion());
      net.kyori.adventure.title.Title.Times times = title.times();
      if (times != null) {
        timesPkt.setFadeIn((int) DurationUtils.toTicks(times.fadeIn()));
        timesPkt.setStay((int) DurationUtils.toTicks(times.stay()));
        timesPkt.setFadeOut((int) DurationUtils.toTicks(times.fadeOut()));
      }
      connection.delayedWrite(timesPkt);

      connection.flush();
    }
  }

  @Override
  public void clearTitle() {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      connection.write(GenericTitlePacket.constructTitlePacket(
              GenericTitlePacket.ActionType.HIDE, this.getProtocolVersion()));
    }
  }

  @Override
  public void resetTitle() {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      connection.write(GenericTitlePacket.constructTitlePacket(
                      GenericTitlePacket.ActionType.RESET, this.getProtocolVersion()));
    }
  }

  @Override
  public void hideBossBar(@NonNull BossBar bar) {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_9) >= 0) {
      this.server.getBossBarManager().removeBossBar(this, bar);
    }
  }

  @Override
  public void showBossBar(@NonNull BossBar bar) {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_9) >= 0) {
      this.server.getBossBarManager().addBossBar(this, bar);
    }
  }

  @Override
  public ConnectionRequestBuilder createConnectionRequest(RegisteredServer server) {
    return new ConnectionRequestBuilderImpl(server);
  }

  @Override
  public List<GameProfile.Property> getGameProfileProperties() {
    return this.profile.getProperties();
  }

  @Override
  public void setGameProfileProperties(List<GameProfile.Property> properties) {
    this.profile = profile.withProperties(Preconditions.checkNotNull(properties));
  }

  @Override
  public void clearHeaderAndFooter() {
    tabList.clearHeaderAndFooter();
  }

  @Override
  public VelocityTabList getTabList() {
    return tabList;
  }

  @Override
  public void disconnect(Component reason) {
    if (connection.eventLoop().inEventLoop()) {
      disconnect0(reason, false);
    } else {
      connection.eventLoop().execute(() -> disconnect0(reason, false));
    }
  }

  /**
   * Disconnects the player from the proxy.
   * @param reason the reason for disconnecting the player
   * @param duringLogin whether the disconnect happened during login
   */
  public void disconnect0(Component reason, boolean duringLogin) {
    Component translated = this.translateMessage(reason);

    logger.info("{} has disconnected: {}", this,
        LegacyComponentSerializer.legacySection().serialize(translated));
    connection.closeWith(Disconnect.create(translated, this.getProtocolVersion()));
  }

  public @Nullable VelocityServerConnection getConnectedServer() {
    return connectedServer;
  }

  public @Nullable VelocityServerConnection getConnectionInFlight() {
    return connectionInFlight;
  }

  public void resetInFlightConnection() {
    connectionInFlight = null;
  }

  /**
   * Handles unexpected disconnects.
   * @param server the server we disconnected from
   * @param throwable the exception
   * @param safe whether or not we can safely reconnect to a new server
   */
  public void handleConnectionException(RegisteredServer server, Throwable throwable,
      boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    if (throwable == null) {
      throw new NullPointerException("throwable");
    }

    Throwable wrapped = throwable;
    if (throwable instanceof CompletionException) {
      Throwable cause = throwable.getCause();
      if (cause != null) {
        wrapped = cause;
      }
    }

    Component friendlyError;
    if (connectedServer != null && connectedServer.getServerInfo().equals(server.getServerInfo())) {
      friendlyError = Component.translatable("velocity.error.connected-server-error",
          Component.text(server.getServerInfo().getName()));
    } else {
      logger.error("{}: unable to connect to server {}", this, server.getServerInfo().getName(),
          wrapped);
      friendlyError = Component.translatable("velocity.error.connecting-server-error",
          Component.text(server.getServerInfo().getName()));
    }
    handleConnectionException(server, null, friendlyError.color(NamedTextColor.RED), safe);
  }

  /**
   * Handles unexpected disconnects.
   * @param server the server we disconnected from
   * @param disconnect the disconnect packet
   * @param safe whether or not we can safely reconnect to a new server
   */
  public void handleConnectionException(RegisteredServer server, Disconnect disconnect,
      boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    Component disconnectReason = GsonComponentSerializer.gson().deserialize(disconnect.getReason());
    String plainTextReason = PASS_THRU_TRANSLATE.serialize(disconnectReason);
    if (connectedServer != null && connectedServer.getServerInfo().equals(server.getServerInfo())) {
      logger.info("{}: kicked from server {}: {}", this, server.getServerInfo().getName(),
          plainTextReason);
      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.moved-to-new-server", NamedTextColor.RED,
              Component.text(server.getServerInfo().getName()),
              disconnectReason), safe);
    } else {
      logger.error("{}: disconnected while connecting to {}: {}", this,
          server.getServerInfo().getName(), plainTextReason);
      handleConnectionException(server, disconnectReason,
          Component.translatable("velocity.error.cant-connect", NamedTextColor.RED,
              Component.text(server.getServerInfo().getName()),
              disconnectReason), safe);
    }
  }

  private void handleConnectionException(RegisteredServer rs,
      @Nullable Component kickReason, Component friendlyReason, boolean safe) {
    if (!isActive()) {
      // If the connection is no longer active, it makes no sense to try and recover it.
      return;
    }

    if (!safe) {
      // /!\ IT IS UNSAFE TO CONTINUE /!\
      //
      // This is usually triggered by a failed Forge handshake.
      disconnect(friendlyReason);
      return;
    }

    boolean kickedFromCurrent = connectedServer == null || connectedServer.getServer().equals(rs);
    ServerKickResult result;
    if (kickedFromCurrent) {
      Optional<RegisteredServer> next = getNextServerToTry(rs);
      result = next.map(RedirectPlayer::create)
          .orElseGet(() -> DisconnectPlayer.create(friendlyReason));
    } else {
      // If we were kicked by going to another server, the connection should not be in flight
      if (connectionInFlight != null && connectionInFlight.getServer().equals(rs)) {
        resetInFlightConnection();
      }
      result = Notify.create(friendlyReason);
    }
    KickedFromServerEvent originalEvent = new KickedFromServerEvent(this, rs, kickReason,
        !kickedFromCurrent, result);
    handleKickEvent(originalEvent, friendlyReason, kickedFromCurrent);
  }

  private void handleKickEvent(KickedFromServerEvent originalEvent, Component friendlyReason,
      boolean kickedFromCurrent) {
    server.getEventManager().fire(originalEvent)
        .thenAcceptAsync(event -> {
          // There can't be any connection in flight now.
          connectionInFlight = null;

          // Make sure we clear the current connected server as the connection is invalid.
          boolean previouslyConnected = connectedServer != null;
          if (kickedFromCurrent) {
            connectedServer = null;
          }

          if (!isActive()) {
            // If the connection is no longer active, it makes no sense to try and recover it.
            return;
          }

          if (event.getResult() instanceof DisconnectPlayer) {
            DisconnectPlayer res = (DisconnectPlayer) event.getResult();
            disconnect(res.getReasonComponent());
          } else if (event.getResult() instanceof RedirectPlayer) {
            RedirectPlayer res = (RedirectPlayer) event.getResult();
            createConnectionRequest(res.getServer())
                .connect()
                .whenCompleteAsync((status, throwable) -> {
                  if (throwable != null) {
                    handleConnectionException(status != null ? status.getAttemptedConnection()
                        : res.getServer(), throwable, true);
                    return;
                  }

                  switch (status.getStatus()) {
                    // Impossible/nonsensical cases
                    case ALREADY_CONNECTED:
                    case CONNECTION_IN_PROGRESS:
                    // Fatal case
                    case CONNECTION_CANCELLED:
                      Component fallbackMsg = res.getMessageComponent();
                      if (fallbackMsg == null) {
                        fallbackMsg = friendlyReason;
                      }
                      disconnect(status.getReasonComponent().orElse(fallbackMsg));
                      break;
                    case SERVER_DISCONNECTED:
                      Component reason = status.getReasonComponent()
                          .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
                      handleConnectionException(res.getServer(), Disconnect.create(reason,
                          getProtocolVersion()), ((Impl) status).isSafe());
                      break;
                    case SUCCESS:
                      Component requestedMessage = res.getMessageComponent();
                      if (requestedMessage == null) {
                        requestedMessage = Component.translatable("velocity.error.moved-to-new-server",
                            NamedTextColor.RED,
                            Component.text(originalEvent.getServer().getServerInfo().getName()),
                            friendlyReason);
                      }
                      sendMessage(requestedMessage);
                      break;
                    default:
                      // The only remaining value is successful (no need to do anything!)
                      break;
                  }
                }, connection.eventLoop());
          } else if (event.getResult() instanceof Notify) {
            Notify res = (Notify) event.getResult();
            if (event.kickedDuringServerConnect() && previouslyConnected) {
              sendMessage(Identity.nil(), res.getMessageComponent());
            } else {
              disconnect(res.getMessageComponent());
            }
          } else {
            // In case someone gets creative, assume we want to disconnect the player.
            disconnect(friendlyReason);
          }
        }, connection.eventLoop());
  }

  /**
   * Finds another server to attempt to log into, if we were unexpectedly disconnected from the
   * server.
   *
   * @return the next server to try
   */
  public Optional<RegisteredServer> getNextServerToTry() {
    return this.getNextServerToTry(null);
  }

  /**
   * Finds another server to attempt to log into, if we were unexpectedly disconnected from the
   * server.
   *
   * @param current the "current" server that the player is on, useful as an override
   *
   * @return the next server to try
   */
  private Optional<RegisteredServer> getNextServerToTry(@Nullable RegisteredServer current) {
    if (serversToTry == null) {
      String virtualHostStr = getVirtualHost().map(InetSocketAddress::getHostString)
          .orElse("")
          .toLowerCase(Locale.ROOT);
      serversToTry = server.getConfiguration().getForcedHosts().getOrDefault(virtualHostStr,
          Collections.emptyList());
    }

    if (serversToTry.isEmpty()) {
      serversToTry = server.getConfiguration().getAttemptConnectionOrder();
    }

    for (int i = tryIndex; i < serversToTry.size(); i++) {
      String toTryName = serversToTry.get(i);
      if ((connectedServer != null && hasSameName(connectedServer.getServer(), toTryName))
          || (connectionInFlight != null && hasSameName(connectionInFlight.getServer(), toTryName))
          || (current != null && hasSameName(current, toTryName))) {
        continue;
      }

      tryIndex = i;
      return server.getServer(toTryName);
    }
    return Optional.empty();
  }

  private static boolean hasSameName(RegisteredServer server, String name) {
    return server.getServerInfo().getName().equalsIgnoreCase(name);
  }

  /**
   * Sets the player's new connected server and clears the in-flight connection.
   *
   * @param serverConnection the new server connection
   */
  public void setConnectedServer(@Nullable VelocityServerConnection serverConnection) {
    this.connectedServer = serverConnection;
    this.tryIndex = 0; // reset since we got connected to a server

    if (serverConnection == connectionInFlight) {
      connectionInFlight = null;
    }
  }

  public void sendLegacyForgeHandshakeResetPacket() {
    connectionPhase.resetConnectionPhase(this);
  }

  private MinecraftConnection ensureBackendConnection() {
    VelocityServerConnection sc = this.connectedServer;
    if (sc == null) {
      throw new IllegalStateException("No backend connection");
    }

    MinecraftConnection mc = sc.getConnection();
    if (mc == null) {
      throw new IllegalStateException("Backend connection is not connected to a server");
    }

    return mc;
  }

  void teardown() {
    if (connectionInFlight != null) {
      connectionInFlight.disconnect();
    }
    if (connectedServer != null) {
      connectedServer.disconnect();
    }

    Optional<Player> connectedPlayer = server.getPlayer(this.getUniqueId());
    server.unregisterConnection(this);

    DisconnectEvent.LoginStatus status;
    if (connectedPlayer.isPresent()) {
      if (!connectedPlayer.get().getCurrentServer().isPresent()) {
        status = LoginStatus.PRE_SERVER_JOIN;
      } else {
        status = connectedPlayer.get() == this ? LoginStatus.SUCCESSFUL_LOGIN
            : LoginStatus.CONFLICTING_LOGIN;
      }
    } else {
      status = connection.isKnownDisconnect() ? LoginStatus.CANCELLED_BY_PROXY :
          LoginStatus.CANCELLED_BY_USER;
    }

    DisconnectEvent event = new DisconnectEvent(this, status);
    server.getEventManager().fire(event).whenComplete((val, ex) -> {
      if (ex == null) {
        this.teardownFuture.complete(null);
      } else {
        this.teardownFuture.completeExceptionally(ex);
      }
    });
  }

  public CompletableFuture<Void> getTeardownFuture() {
    return teardownFuture;
  }

  @Override
  public String toString() {
    return "[connected player] " + profile.getName() + " (" + getRemoteAddress() + ")";
  }

  @Override
  public Tristate getPermissionValue(String permission) {
    return permissionFunction.getPermissionValue(permission);
  }

  @Override
  public boolean sendPluginMessage(ChannelIdentifier identifier, byte[] data) {
    Preconditions.checkNotNull(identifier, "identifier");
    Preconditions.checkNotNull(data, "data");
    PluginMessage message = new PluginMessage(identifier.getId(), Unpooled.wrappedBuffer(data));
    connection.write(message);
    return true;
  }

  @Override
  public String getClientBrand() {
    return clientBrand;
  }

  void setClientBrand(String clientBrand) {
    this.clientBrand = clientBrand;
  }

  @Override
  public void spoofChatInput(String input) {
    Preconditions.checkArgument(input.length() <= Chat.MAX_SERVERBOUND_MESSAGE_LENGTH,
        "input cannot be greater than " + Chat.MAX_SERVERBOUND_MESSAGE_LENGTH
            + " characters in length");
    ensureBackendConnection().write(Chat.createServerbound(input));
  }

  @Override
  @Deprecated
  public void sendResourcePack(String url) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).build());
  }

  @Override
  @Deprecated
  public void sendResourcePack(String url, byte[] hash) {
    sendResourcePackOffer(new VelocityResourcePackInfo.BuilderImpl(url).setHash(hash).build());
  }

  @Override
  public void sendResourcePackOffer(ResourcePackInfo packInfo) {
    if (this.getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      Preconditions.checkNotNull(packInfo, "packInfo");
      queueResourcePack(packInfo);
    }
  }

  /**
   * Queues a resource-pack for sending to the player and sends it
   * immediately if the queue is empty.
   */
  public void queueResourcePack(ResourcePackInfo info) {
    outstandingResourcePacks.add(info);
    if (outstandingResourcePacks.size() == 1) {
      tickResourcePackQueue();
    }
  }

  private void tickResourcePackQueue() {
    ResourcePackInfo queued = outstandingResourcePacks.peek();

    if (queued != null) {
      // Check if the player declined a resource pack once already
      if (previousResourceResponse != null && !previousResourceResponse) {
        // If that happened we can flush the queue right away.
        // Unless its 1.17+ and forced it will come back denied anyway
        while (!outstandingResourcePacks.isEmpty()) {
          queued = outstandingResourcePacks.peek();
          if (queued.getShouldForce() && getProtocolVersion()
                  .compareTo(ProtocolVersion.MINECRAFT_1_17) >= 0) {
            break;
          }
          onResourcePackResponse(PlayerResourcePackStatusEvent.Status.DECLINED);
          queued = null;
        }
        if (queued == null) {
          // Exit as the queue was cleared
          return;
        }
      }

      ResourcePackRequest request = new ResourcePackRequest();
      request.setUrl(queued.getUrl());
      if (queued.getHash() != null) {
        request.setHash(ByteBufUtil.hexDump(queued.getHash()));
      } else {
        request.setHash("");
      }
      request.setRequired(queued.getShouldForce());
      request.setPrompt(queued.getPrompt());

      connection.write(request);
    }
  }

  @Override
  public @Nullable ResourcePackInfo getAppliedResourcePack() {
    return appliedResourcePack;
  }

  @Override
  public @Nullable ResourcePackInfo getPendingResourcePack() {
    return pendingResourcePack;
  }

  /**
   * Processes a client response to a sent resource-pack.
   */
  public boolean onResourcePackResponse(PlayerResourcePackStatusEvent.Status status) {
    final boolean peek = status == PlayerResourcePackStatusEvent.Status.ACCEPTED;
    final ResourcePackInfo queued = peek
            ? outstandingResourcePacks.peek() : outstandingResourcePacks.poll();

    server.getEventManager().fire(new PlayerResourcePackStatusEvent(this, status, queued))
            .thenAcceptAsync(event -> {
              if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED
                      && event.getPackInfo() != null && event.getPackInfo().getShouldForce()
                      && (!event.isOverwriteKick() || event.getPlayer()
                              .getProtocolVersion().compareTo(ProtocolVersion.MINECRAFT_1_17) >= 0)
              ) {
                event.getPlayer().disconnect(Component
                        .translatable("multiplayer.requiredTexturePrompt.disconnect"));
              }
            });

    switch (status) {
      case ACCEPTED:
        previousResourceResponse = true;
        pendingResourcePack = queued;
        break;
      case DECLINED:
        previousResourceResponse = false;
        break;
      case SUCCESSFUL:
        appliedResourcePack = queued;
        pendingResourcePack = null;
        break;
      case FAILED_DOWNLOAD:
        pendingResourcePack = null;
        break;
      default:
        break;
    }

    if (!peek) {
      connection.eventLoop().execute(this::tickResourcePackQueue);
    }

    return queued != null && queued.getOrigin() == ResourcePackInfo.Origin.DOWNSTREAM_SERVER;
  }

  /**
   * Sends a {@link KeepAlive} packet to the player with a random ID.
   * The response will be ignored by Velocity as it will not match the
   * ID last sent by the server.
   */
  public void sendKeepAlive() {
    if (connection.getState() == StateRegistry.PLAY) {
      KeepAlive keepAlive = new KeepAlive();
      keepAlive.setRandomId(ThreadLocalRandom.current().nextLong());
      connection.write(keepAlive);
    }
  }

  /**
   * Gets the current "phase" of the connection, mostly used for tracking
   * modded negotiation for legacy forge servers and provides methods
   * for performing phase specific actions.
   *
   * @return The {@link ClientConnectionPhase}
   */
  public ClientConnectionPhase getPhase() {
    return connectionPhase;
  }

  /**
   * Sets the current "phase" of the connection. See {@link #getPhase()}
   *
   * @param connectionPhase The {@link ClientConnectionPhase}
   */
  public void setPhase(ClientConnectionPhase connectionPhase) {
    this.connectionPhase = connectionPhase;
  }

  /**
   * Return all the plugin message channels "known" to the client.
   * @return the channels
   */
  public Collection<String> getKnownChannels() {
    return knownChannels;
  }

  private class IdentityImpl implements Identity {
    @Override
    public @NonNull UUID uuid() {
      return ConnectedPlayer.this.getUniqueId();
    }
  }

  private class ConnectionRequestBuilderImpl implements ConnectionRequestBuilder {

    private final RegisteredServer toConnect;

    ConnectionRequestBuilderImpl(RegisteredServer toConnect) {
      this.toConnect = Preconditions.checkNotNull(toConnect, "info");
    }

    @Override
    public RegisteredServer getServer() {
      return toConnect;
    }

    private Optional<ConnectionRequestBuilder.Status> checkServer(RegisteredServer server) {
      Preconditions.checkArgument(server instanceof VelocityRegisteredServer,
          "Not a valid Velocity server.");
      if (connectionInFlight != null || (connectedServer != null
          && !connectedServer.hasCompletedJoin())) {
        return Optional.of(ConnectionRequestBuilder.Status.CONNECTION_IN_PROGRESS);
      }
      if (connectedServer != null
              && connectedServer.getServer().getServerInfo().equals(server.getServerInfo())) {
        return Optional.of(ALREADY_CONNECTED);
      }
      return Optional.empty();
    }

    private CompletableFuture<Optional<Status>> getInitialStatus() {
      return CompletableFuture.supplyAsync(() -> checkServer(toConnect), connection.eventLoop());
    }

    private CompletableFuture<Impl> internalConnect() {
      return this.getInitialStatus()
          .thenCompose(initialCheck -> {
            if (initialCheck.isPresent()) {
              return completedFuture(plainResult(initialCheck.get(), toConnect));
            }

            ServerPreConnectEvent event = new ServerPreConnectEvent(ConnectedPlayer.this,
                toConnect);
            return server.getEventManager().fire(event)
                .thenComposeAsync(newEvent -> {
                  Optional<RegisteredServer> newDest = newEvent.getResult().getServer();
                  if (!newDest.isPresent()) {
                    return completedFuture(
                        plainResult(ConnectionRequestBuilder.Status.CONNECTION_CANCELLED, toConnect)
                    );
                  }

                  RegisteredServer realDestination = newDest.get();
                  Optional<ConnectionRequestBuilder.Status> check = checkServer(realDestination);
                  if (check.isPresent()) {
                    return completedFuture(plainResult(check.get(), realDestination));
                  }

                  VelocityRegisteredServer vrs = (VelocityRegisteredServer) realDestination;
                  VelocityServerConnection con = new VelocityServerConnection(vrs,
                      ConnectedPlayer.this, server);
                  connectionInFlight = con;
                  return con.connect().whenCompleteAsync(
                      (result, exception) -> this.resetIfInFlightIs(con), connection.eventLoop());
                }, connection.eventLoop());
          });
    }

    private void resetIfInFlightIs(VelocityServerConnection establishedConnection) {
      if (establishedConnection == connectionInFlight) {
        resetInFlightConnection();
      }
    }

    @Override
    public CompletableFuture<Result> connect() {
      return this.internalConnect()
          .whenCompleteAsync((status, throwable) -> {
            if (status != null && !status.isSuccessful()) {
              if (!status.isSafe()) {
                handleConnectionException(status.getAttemptedConnection(), throwable, false);
                return;
              }
            }
            if (throwable != null) {
              logger.error("Exception during connect; status = {}", status, throwable);
            }
          }, connection.eventLoop())
          .thenApply(x -> x);
    }

    @Override
    public CompletableFuture<Boolean> connectWithIndication() {
      return internalConnect()
          .whenCompleteAsync((status, throwable) -> {
            if (throwable != null) {
              // TODO: The exception handling from this is not very good. Find a better way.
              handleConnectionException(status != null ? status.getAttemptedConnection()
                  : toConnect, throwable, true);
              return;
            }

            switch (status.getStatus()) {
              case ALREADY_CONNECTED:
                sendMessage(Identity.nil(), ConnectionMessages.ALREADY_CONNECTED);
                break;
              case CONNECTION_IN_PROGRESS:
                sendMessage(Identity.nil(), ConnectionMessages.IN_PROGRESS);
                break;
              case CONNECTION_CANCELLED:
                // Ignored; the plugin probably already handled this.
                break;
              case SERVER_DISCONNECTED:
                Component reason = status.getReasonComponent()
                    .orElse(ConnectionMessages.INTERNAL_SERVER_CONNECTION_ERROR);
                handleConnectionException(toConnect, Disconnect.create(reason,
                    getProtocolVersion()), status.isSafe());
                break;
              default:
                // The only remaining value is successful (no need to do anything!)
                break;
            }
          }, connection.eventLoop())
          .thenApply(Result::isSuccessful);
    }

    @Override
    public void fireAndForget() {
      connectWithIndication();
    }
  }
}

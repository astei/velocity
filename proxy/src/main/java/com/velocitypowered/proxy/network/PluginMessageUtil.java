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

package com.velocitypowered.proxy.network;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.messages.KeyedPluginChannelId;
import com.velocitypowered.api.proxy.messages.PairedPluginChannelId;
import com.velocitypowered.api.proxy.messages.PluginChannelId;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.proxy.network.packet.AbstractPluginMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PluginMessageUtil {

  private static final String BRAND_CHANNEL_LEGACY = "MC|Brand";
  private static final String BRAND_CHANNEL = "minecraft:brand";
  private static final String REGISTER_CHANNEL_LEGACY = "REGISTER";
  private static final String REGISTER_CHANNEL = "minecraft:register";
  private static final String UNREGISTER_CHANNEL_LEGACY = "UNREGISTER";
  private static final String UNREGISTER_CHANNEL = "minecraft:unregister";

  private PluginMessageUtil() {
    throw new AssertionError();
  }

  /**
   * Determines whether or not this is a brand plugin message. This is shown on the client.
   * @param message the plugin message
   * @return whether or not this is a brand plugin message
   */
  public static boolean isMcBrand(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(BRAND_CHANNEL_LEGACY) || message.getChannel()
        .equals(BRAND_CHANNEL);
  }

  /**
   * Determines whether or not this plugin message is being used to register plugin channels.
   * @param message the plugin message
   * @return whether we are registering plugin channels or not
   */
  public static boolean isRegister(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(REGISTER_CHANNEL_LEGACY) || message.getChannel()
        .equals(REGISTER_CHANNEL);
  }

  /**
   * Determines whether or not this plugin message is being used to unregister plugin channels.
   * @param message the plugin message
   * @return whether we are unregistering plugin channels or not
   */
  public static boolean isUnregister(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(UNREGISTER_CHANNEL_LEGACY) || message.getChannel()
        .equals(UNREGISTER_CHANNEL);
  }

  /**
   * Determines whether or not this plugin message is a legacy (&lt;1.13) registration plugin message.
   * @param message the plugin message
   * @return whether this is a legacy register message
   */
  public static boolean isLegacyRegister(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(REGISTER_CHANNEL_LEGACY);
  }

  /**
   * Determines whether or not this plugin message is a legacy (&lt;1.13) unregistration plugin
   * message.
   * @param message the plugin message
   * @return whether this is a legacy unregister message
   */
  public static boolean isLegacyUnregister(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    return message.getChannel().equals(UNREGISTER_CHANNEL_LEGACY);
  }

  /**
   * Fetches all the channels in a register or unregister plugin message.
   * @param message the message to get the channels from
   * @return the channels, as an immutable list
   */
  public static List<String> getChannels(AbstractPluginMessagePacket<?> message) {
    checkNotNull(message, "message");
    checkArgument(isRegister(message) || isUnregister(message), "Unknown channel type %s",
            message.getChannel());
    if (!message.content().isReadable()) {
      // If we try to split this, we will get an one-element array with the empty string, which
      // has caused issues with 1.13+ compatibility. Just return an empty list.
      return ImmutableList.of();
    }
    String channels = message.content().toString(StandardCharsets.UTF_8);
    return ImmutableList.copyOf(channels.split("\0"));
  }

  /**
   * Constructs a channel (un)register packet.
   * @param protocolVersion the client/server's protocol version
   * @param channels the channels to register
   * @return the plugin message to send
   */
  public static AbstractPluginMessagePacket<?> constructChannelsPacket(ProtocolVersion protocolVersion,
                                                            Collection<String> channels,
                                                            AbstractPluginMessagePacket.Factory<?> factory) {
    checkNotNull(channels, "channels");
    checkArgument(!channels.isEmpty(), "no channels specified");
    String channelName = protocolVersion.gte(ProtocolVersion.MINECRAFT_1_13)
        ? REGISTER_CHANNEL : REGISTER_CHANNEL_LEGACY;
    ByteBuf contents = Unpooled.buffer();
    contents.writeCharSequence(String.join("\0", channels), StandardCharsets.UTF_8);
    return factory.create(channelName, contents);
  }

  /**
   * Rewrites the brand message to indicate the presence of Velocity.
   * @param message the plugin message
   * @param version the proxy version
   * @return the rewritten plugin message
   */
  public static AbstractPluginMessagePacket<?> rewriteMinecraftBrand(AbstractPluginMessagePacket<?> message,
                                                          ProxyVersion version,
                                                          ProtocolVersion protocolVersion,
                                                          AbstractPluginMessagePacket.Factory<?> factory) {
    checkNotNull(message, "message");
    checkNotNull(version, "version");
    checkArgument(isMcBrand(message), "message is not a brand plugin message");

    String currentBrand = readBrandMessage(message.content());
    String rewrittenBrand = String.format("%s (%s)", currentBrand, version.name());

    ByteBuf rewrittenBuf = Unpooled.buffer();
    if (protocolVersion.gte(ProtocolVersion.MINECRAFT_1_8)) {
      ProtocolUtils.writeString(rewrittenBuf, rewrittenBrand);
    } else {
      rewrittenBuf.writeCharSequence(rewrittenBrand, StandardCharsets.UTF_8);
    }

    return factory.create(message.getChannel(), rewrittenBuf);
  }

  private static String readBrandMessage(ByteBuf content) {
    // Some clients (mostly poorly-implemented bots) do not send validly-formed brand messages.
    // In order to accommodate their broken behavior, we'll first try to read in the 1.8 format, and
    // if that fails, treat it as a 1.7-format message (which has no prefixed length). (The message
    // Velocity sends will be in the correct format depending on the protocol.)
    try {
      return ProtocolUtils.readString(content.slice());
    } catch (Exception e) {
      return ProtocolUtils.readStringWithoutLength(content.slice());
    }
  }

  public static String channelIdForVersion(PluginChannelId id, ProtocolVersion version) {
    if (id instanceof KeyedPluginChannelId) {
      return ((KeyedPluginChannelId) id).key().asString();
    } else if (id instanceof PairedPluginChannelId) {
      if (version.gte(ProtocolVersion.MINECRAFT_1_13)) {
        return ((PairedPluginChannelId) id).modernChannelKey().asString();
      } else {
        return ((PairedPluginChannelId) id).legacyChannel();
      }
    }
    throw new IllegalArgumentException("Unknown channel ID " + id.getClass().getName());
  }

  private static final Pattern INVALID_IDENTIFIER_REGEX = Pattern.compile("[^a-z0-9\\-_]*");

  /**
   * Transform a plugin message channel from a "legacy" (&lt;1.13) form to a modern one.
   * @param name the existing name
   * @return the new name
   */
  public static String transformLegacyToModernChannel(String name) {
    checkNotNull(name, "name");

    if (name.indexOf(':') != -1) {
      // Probably valid. We won't check this for now and go on faith.
      return name;
    }

    // Before falling into the fallback, explicitly rewrite certain messages.
    switch (name) {
      case REGISTER_CHANNEL_LEGACY:
        return REGISTER_CHANNEL;
      case UNREGISTER_CHANNEL_LEGACY:
        return UNREGISTER_CHANNEL;
      case BRAND_CHANNEL_LEGACY:
        return BRAND_CHANNEL;
      case "BungeeCord":
        // This is a special historical case we are compelled to support for the benefit of
        // BungeeQuack.
        return "bungeecord:main";
      default:
        // This is very likely a legacy name, so transform it. Velocity uses the same scheme as
        // BungeeCord does to transform channels, but also removes clearly invalid characters as
        // well.
        String lower = name.toLowerCase(Locale.ROOT);
        return "legacy:" + INVALID_IDENTIFIER_REGEX.matcher(lower).replaceAll("");
    }
  }
}

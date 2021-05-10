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

package com.velocitypowered.proxy.console;

import static com.velocitypowered.api.permission.PermissionFunction.ALWAYS_TRUE;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.io.IoBuilder;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class VelocityConsoleCommandSource implements ConsoleCommandSource {

  private static final Logger logger = LogManager.getLogger(VelocityConsoleCommandSource.class);

  private PermissionFunction permissionFunction = ALWAYS_TRUE;

  @Override
  public void sendMessage(net.kyori.text.Component component) {
    logger.info(net.kyori.text.serializer.legacy.LegacyComponentSerializer.legacy()
        .serialize(component));
  }

  @Override
  public void sendMessage(@NonNull Identity identity, @NonNull Component message) {
    logger.info(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
        .serialize(message));
  }

  @Override
  public @NonNull Tristate getPermissionValue(@NonNull String permission) {
    return this.permissionFunction.getPermissionValue(permission);
  }

  /**
   * Sets up {@code System.out} and {@code System.err} to redirect to log4j.
   */
  public void setupStreams() {
    System.setOut(IoBuilder.forLogger(logger).setLevel(Level.INFO).buildPrintStream());
    System.setErr(IoBuilder.forLogger(logger).setLevel(Level.ERROR).buildPrintStream());
  }

  /**
   * Sets up permissions for the console.
   * @param eventManager the event manager
   */
  public void setupPermissions(EventManager eventManager) {
    PermissionsSetupEvent event = new PermissionsSetupEvent(this, s -> ALWAYS_TRUE);
    // we can safely block here, this is before any listeners fire
    this.permissionFunction = eventManager.fire(event).join().createFunction(this);
    if (this.permissionFunction == null) {
      logger.error(
          "A plugin permission provider {} provided an invalid permission function"
              + " for the console. This is a bug in the plugin, not in Velocity. Falling"
              + " back to the default permission function.",
          event.getProvider().getClass().getName());
      this.permissionFunction = ALWAYS_TRUE;
    }
  }

}

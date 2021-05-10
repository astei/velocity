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

import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.proxy.VelocityServer;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecrell.terminalconsole.SimpleTerminalConsole;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

public final class VelocityTerminalConsole extends SimpleTerminalConsole {

  private static final Logger logger = LogManager.getLogger(VelocityTerminalConsole.class);

  private final ConsoleCommandSource consoleCommandSource;
  private final VelocityServer server;

  public VelocityTerminalConsole(ConsoleCommandSource consoleCommandSource, VelocityServer server) {
    this.consoleCommandSource = consoleCommandSource;
    this.server = server;
  }

  @Override
  protected LineReader buildReader(LineReaderBuilder builder) {
    return super.buildReader(builder
            .appName("Velocity")
            .completer((reader, parsedLine, list) -> {
              try {
                List<String> offers = this.server.getCommandManager()
                        .offerSuggestions(consoleCommandSource, parsedLine.line())
                        .join(); // Console doesn't get harmed much by this...
                for (String offer : offers) {
                  list.add(new Candidate(offer));
                }
              } catch (Exception e) {
                logger.error("An error occurred while trying to perform tab completion.", e);
              }
            })
    );
  }

  @Override
  protected boolean isRunning() {
    return !this.server.isShutdown();
  }

  @Override
  protected void runCommand(String command) {
    try {
      if (!this.server.getCommandManager().execute(consoleCommandSource, command)) {
        consoleCommandSource.sendMessage(Component.text("Command not found.", NamedTextColor.RED));
      }
    } catch (Exception e) {
      logger.error("An error occurred while running this command.", e);
    }
  }

  @Override
  protected void shutdown() {
    this.server.shutdown(true);
  }
}

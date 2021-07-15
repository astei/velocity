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

package com.velocitypowered.proxy.command;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.RootCommandNode;
import com.spotify.futures.CompletableFutures;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.proxy.command.registrar.BrigadierCommandRegistrar;
import com.velocitypowered.proxy.command.registrar.CommandRegistrar;
import com.velocitypowered.proxy.command.registrar.RawCommandRegistrar;
import com.velocitypowered.proxy.command.registrar.SimpleCommandRegistrar;
import com.velocitypowered.proxy.event.VelocityEventManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.jetbrains.annotations.VisibleForTesting;

public class VelocityCommandManager implements CommandManager {

  private final @GuardedBy("lock") CommandDispatcher<CommandSource> dispatcher;
  private final ReadWriteLock lock;

  private final VelocityEventManager eventManager;
  private final List<CommandRegistrar<?>> registrars;
  private final SuggestionsProvider<CommandSource> suggestionsProvider;
  private final CommandGraphInjector<CommandSource> injector;
  private final Map<String, CommandMeta> commandMeta;

  /**
   * Constructs a command manager.
   *
   * @param eventManager the event manager
   */
  public VelocityCommandManager(final VelocityEventManager eventManager) {
    this.lock = new ReentrantReadWriteLock();
    this.dispatcher = new CommandDispatcher<>();
    this.eventManager = Preconditions.checkNotNull(eventManager);
    final RootCommandNode<CommandSource> root = this.dispatcher.getRoot();
    this.registrars = ImmutableList.of(
            new BrigadierCommandRegistrar(root, this.lock.writeLock()),
            new SimpleCommandRegistrar(root, this.lock.writeLock()),
            new RawCommandRegistrar(root, this.lock.writeLock()));
    this.suggestionsProvider = new SuggestionsProvider<>(this.dispatcher, this.lock.readLock());
    this.injector = new CommandGraphInjector<>(this.dispatcher, this.lock.readLock());
    this.commandMeta = Maps.newConcurrentMap();
  }

  public void setAnnounceProxyCommands(boolean announceProxyCommands) {
    this.suggestionsProvider.setAnnounceProxyCommands(announceProxyCommands);
  }

  @Override
  public CommandMeta.Builder metaBuilder(final String alias) {
    Preconditions.checkNotNull(alias, "alias");
    return new VelocityCommandMeta.Builder(alias);
  }

  @Override
  public CommandMeta.Builder metaBuilder(final BrigadierCommand command) {
    Preconditions.checkNotNull(command, "command");
    return new VelocityCommandMeta.Builder(command.getNode().getName());
  }

  @Override
  public void register(final BrigadierCommand command) {
    Preconditions.checkNotNull(command, "command");
    register(metaBuilder(command).build(), command);
  }

  @Override
  public void register(final CommandMeta meta, final Command command) {
    Preconditions.checkNotNull(meta, "meta");
    Preconditions.checkNotNull(command, "command");

    // TODO Warn if command implements multiple registrable interfaces?
    for (final CommandRegistrar<?> registrar : this.registrars) {
      if (this.tryRegister(registrar, command, meta)) {
        return; // success
      }
    }
    throw new IllegalArgumentException(
            command + " does not implement a registrable Command subinterface");
  }

  /**
   * Attempts to register the given command if it implements the
   * {@linkplain CommandRegistrar#registrableSuperInterface() registrable superinterface}
   * of the given registrar.
   *
   * @param registrar the registrar to register the command
   * @param command the command to register
   * @param meta the command metadata
   * @param <T> the type of the command
   * @return true if the command implements the registrable superinterface of the registrar;
   *         false otherwise.
   * @throws IllegalArgumentException if the registrar cannot register the command
   */
  private <T extends Command> boolean tryRegister(final CommandRegistrar<T> registrar,
                                                  final Command command, final CommandMeta meta) {
    final Class<T> superInterface = registrar.registrableSuperInterface();
    if (!superInterface.isInstance(command)) {
      return false;
    }
    registrar.register(meta, superInterface.cast(command));
    commandMeta.put(meta.getAliases().iterator().next(), meta);
    return true;
  }

  @Override
  public void unregister(final String alias) {
    Preconditions.checkNotNull(alias, "alias");
    // The literals of secondary aliases will preserve the children of
    // the removed literal in the graph.
    String aliasLowercase = alias.toLowerCase(Locale.ENGLISH);
    dispatcher.getRoot().removeChildByName(aliasLowercase);
    commandMeta.remove(aliasLowercase);
  }

  @Override
  public Optional<CommandMeta> getCommandMeta(String alias) {
    Preconditions.checkNotNull(alias, "alias");
    return Optional.ofNullable(commandMeta.get(alias.toLowerCase(Locale.ENGLISH)));
  }

  /**
   * Fires a {@link CommandExecuteEvent}.
   *
   * @param source the source to execute the command for
   * @param cmdLine the command to execute
   * @return the {@link CompletableFuture} of the event
   */
  public CompletableFuture<CommandExecuteEvent> callCommandEvent(final CommandSource source,
      final String cmdLine) {
    Preconditions.checkNotNull(source, "source");
    Preconditions.checkNotNull(cmdLine, "cmdLine");
    return eventManager.fire(new CommandExecuteEvent(source, cmdLine));
  }

  private boolean executeImmediately0(final CommandSource source, final String cmdLine) {
    Preconditions.checkNotNull(source, "source");
    Preconditions.checkNotNull(cmdLine, "cmdLine");

    final String normalizedInput = VelocityCommands.normalizeInput(cmdLine, true);
    try {
      // The parse can fail if the requirement predicates throw
      final ParseResults<CommandSource> parse = this.parse(normalizedInput, source);
      return dispatcher.execute(parse) != BrigadierCommand.FORWARD;
    } catch (final CommandSyntaxException e) {
      boolean isSyntaxError = !e.getType().equals(
          CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand());
      if (isSyntaxError) {
        source.sendMessage(Identity.nil(), Component.text(e.getMessage(), NamedTextColor.RED));
        // This is, of course, a lie, but the API will need to change...
        return true;
      } else {
        return false;
      }
    } catch (final Throwable e) {
      // Ugly, ugly swallowing of everything Throwable, because plugins are naughty.
      throw new RuntimeException("Unable to invoke command " + cmdLine + " for " + source, e);
    }
  }

  @Override
  public CompletableFuture<Boolean> executeAsync(final CommandSource source, final String cmdLine) {
    Preconditions.checkNotNull(source, "source");
    Preconditions.checkNotNull(cmdLine, "cmdLine");

    return callCommandEvent(source, cmdLine).thenApplyAsync(event -> {
      CommandResult commandResult = event.getResult();
      if (commandResult.isForwardToServer() || !commandResult.isAllowed()) {
        return false;
      }
      return executeImmediately0(source, commandResult.getCommand().orElse(event.getCommand()));
    }, eventManager.getAsyncExecutor());
  }

  @Override
  public CompletableFuture<Boolean> executeImmediatelyAsync(
      final CommandSource source, final String cmdLine) {
    Preconditions.checkNotNull(source, "source");
    Preconditions.checkNotNull(cmdLine, "cmdLine");

    return CompletableFuture.supplyAsync(
        () -> executeImmediately0(source, cmdLine), eventManager.getAsyncExecutor());
  }

  /**
   * Returns suggestions to fill in the given command.
   *
   * @param source the source to execute the command for
   * @param cmdLine the partially completed command
   * @return a {@link CompletableFuture} eventually completed with a {@link List},
   *         possibly empty
   */
  public CompletableFuture<List<String>> offerSuggestions(final CommandSource source,
      final String cmdLine) {
    Preconditions.checkNotNull(source, "source");
    Preconditions.checkNotNull(cmdLine, "cmdLine");

    final String normalizedInput = VelocityCommands.normalizeInput(cmdLine, false);
    try {
      return suggestionsProvider.provideSuggestions(normalizedInput, source)
              .thenApply(suggestions ->
                      Lists.transform(suggestions.getList(), Suggestion::getText));
    } catch (final Throwable e) {
      // Again, plugins are naughty
      return CompletableFuture.failedFuture(
        new RuntimeException("Unable to provide suggestions for " + cmdLine + " for " + source, e));
    }
  }

  /**
   * Parses the given command input.
   *
   * @param input the normalized command input, without the leading slash ('/')
   * @param source the command source to parse the command for
   * @return the parse results
   */
  private ParseResults<CommandSource> parse(final String input, final CommandSource source) {
    lock.readLock().lock();
    try {
      return dispatcher.parse(input, source);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns whether the given alias is registered on this manager.
   *
   * @param alias the command alias to check
   * @return true if the alias is registered; false otherwise
   */
  @Override
  public boolean hasCommand(final String alias) {
    Preconditions.checkNotNull(alias, "alias");
    return dispatcher.getRoot().getChild(alias.toLowerCase(Locale.ENGLISH)) != null;
  }

  @VisibleForTesting // this constitutes unsafe publication
  RootCommandNode<CommandSource> getRoot() {
    return dispatcher.getRoot();
  }

  public CommandGraphInjector<CommandSource> getInjector() {
    return injector;
  }
}
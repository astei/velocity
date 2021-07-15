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
import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class VelocityCommandMeta implements CommandMeta {

  static final class Builder implements CommandMeta.Builder {

    private final ImmutableSet.Builder<String> aliases;
    private final ImmutableList.Builder<CommandNode<CommandSource>> hints;
    private Object plugin;

    public Builder(final String alias) {
      Preconditions.checkNotNull(alias, "alias");
      this.aliases = ImmutableSet.<String>builder()
              .add(alias.toLowerCase(Locale.ENGLISH));
      this.hints = ImmutableList.builder();
      this.plugin = null;
    }

    @Override
    public CommandMeta.Builder aliases(final String... aliases) {
      Preconditions.checkNotNull(aliases, "aliases");
      for (int i = 0, length = aliases.length; i < length; i++) {
        final String alias = aliases[i];
        Preconditions.checkNotNull(alias, "alias at index %s", i);
        this.aliases.add(alias.toLowerCase(Locale.ENGLISH));
      }
      return this;
    }

    @Override
    public CommandMeta.Builder hint(final CommandNode<CommandSource> node) {
      Preconditions.checkNotNull(node, "node");
      if (node.getCommand() != null) {
        throw new IllegalArgumentException("Cannot use executable node for hinting");
      }
      if (node.getRedirect() != null) {
        throw new IllegalArgumentException("Cannot use a node with a redirect for hinting");
      }
      this.hints.add(node);
      return this;
    }

    @Override
    public CommandMeta.Builder plugin(Object plugin) {
      Preconditions.checkNotNull(plugin, "plugin");
      this.plugin = plugin;
      return this;
    }

    @Override
    public CommandMeta build() {
      return new VelocityCommandMeta(this.aliases.build(), this.hints.build(), this.plugin);
    }
  }

  /**
   * Creates a node to use for hinting the arguments of a {@link Command}. Hint nodes are
   * sent to 1.13+ clients and the proxy uses them for providing suggestions.
   *
   * <p>A hint node is used to provide suggestions if and only if the requirements of
   * the corresponding {@link CommandNode} are satisfied. The requirement predicate
   * of the returned node always returns {@code false}.
   *
   * @param hint the node containing hinting metadata
   * @return the hinting command node
   */
  private static CommandNode<CommandSource> copyForHinting(final CommandNode<CommandSource> hint) {
    // We need to perform a deep copy of the hint to prevent the user
    // from modifying the nodes and adding a Command or a redirect.
    final ArgumentBuilder<CommandSource, ?> builder = hint.createBuilder()
            // Requirement checking is performed by SuggestionProvider
            .requires(source -> false);
    for (final CommandNode<CommandSource> child : hint.getChildren()) {
      builder.then(copyForHinting(child));
    }
    return builder.build();
  }

  /**
   * Returns a stream of copies of every hint contained in the given metadata object.
   *
   * @param meta the command metadata
   * @return a stream of hinting nodes
   */
  // This is a static method because most methods take a CommandMeta.
  public static Stream<CommandNode<CommandSource>> copyHints(final CommandMeta meta) {
    return meta.getHints().stream().map(VelocityCommandMeta::copyForHinting);
  }

  private final Set<String> aliases;
  private final List<CommandNode<CommandSource>> hints;
  private final Object plugin;

  private VelocityCommandMeta(
          final Set<String> aliases,
          final List<CommandNode<CommandSource>> hints,
          final @Nullable Object plugin
  ) {
    this.aliases = aliases;
    this.hints = hints;
    this.plugin = plugin;
  }

  @Override
  public Collection<String> getAliases() {
    return this.aliases;
  }

  @Override
  public Collection<CommandNode<CommandSource>> getHints() {
    return this.hints;
  }

  @Override
  public Optional<Object> getPlugin() {
    return Optional.ofNullable(plugin);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final VelocityCommandMeta that = (VelocityCommandMeta) o;

    if (!this.aliases.equals(that.aliases)) {
      return false;
    }
    return this.hints.equals(that.hints);
  }

  @Override
  public int hashCode() {
    int result = this.aliases.hashCode();
    result = 31 * result + this.hints.hashCode();
    return result;
  }
}

package com.velocitypowered.proxy.command;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.InvocableCommand;
import java.util.List;
import java.util.Map;

/**
 * Provides parsing methods common to most {@link Command} implementations.
 * In particular, {@link InvocableCommand}s and legacy commands use the same logic for
 * creating and parsing the alias and arguments {@link CommandNode}s, which is contained
 * in this class.
 *
 * <p>A <i>fully-built</i> alias node is a node returned by
 * {@link CommandNodeFactory#create(Command, String)}.
 */
final class VelocityCommands {

  static final String ARGS_NODE_NAME = "arguments";

  // Parsing

  /**
   * Returns the parsed alias, used to execute the command.
   *
   * @param context the context
   * @return the command alias
   */
  static String readAlias(final CommandContext<?> context) {
    return readAlias(context.getNodes());
  }

  /**
   * Returns the parsed alias, used to execute the command.
   *
   * @param context the context builder
   * @return the command alias
   */
  static String readAlias(final CommandContextBuilder<?> context) {
    return readAlias(context.getNodes());
  }

  private static String readAlias(final List<? extends ParsedCommandNode<?>> nodes) {
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException("Cannot read alias from empty node list");
    }
    return nodes.get(0).getNode().getName();
  }

  /**
   * Returns the parsed arguments that come after the command alias, or {@code fallback} if
   * no arguments were provided.
   *
   * @param context the context
   * @param type the type class of the arguments
   * @param fallback the value to return if no arguments were provided
   * @param <V> the type of the arguments
   * @return the command arguments
   */
  static <V> V readArguments(final CommandContext<?> context,
                             final Class<V> type, final V fallback) {
    return readArguments(context.getArguments(), type, fallback);
  }

  /**
   * Returns the parsed arguments that come after the command alias, or {@code fallback} if
   * no arguments were provided.
   *
   * @param context the context builder
   * @param type the type class of the arguments
   * @param fallback the value to return if no arguments were provided
   * @param <V> the type of the arguments
   * @return the command arguments
   */
  static <V> V readArguments(final CommandContextBuilder<?> context,
                             final Class<V> type, final V fallback) {
    return readArguments(context.getArguments(), type, fallback);
  }

  private static <V> V readArguments(final Map<String, ? extends ParsedArgument<?, ?>> arguments,
                                     final Class<V> type, final V fallback) {
    final ParsedArgument<?, ?> argument = arguments.get(ARGS_NODE_NAME);
    if (argument == null) {
      return fallback;
    }
    final Object result = argument.getResult();
    try {
      return type.cast(result);
    } catch (final ClassCastException e) {
      throw new IllegalArgumentException("Parsed argument is of type " + result.getClass()
              + ", expected " + type, e);
    }
  }

  // Creation

  /**
   * Returns a node with the given alias that forwards its execution to the specified target node.
   *
   * @param target the target node
   * @param alias the case-insensitive alias
   * @return a literal node with a redirect to the given target node
   */
  static LiteralCommandNode<CommandSource> createAliasRedirect(
          final LiteralCommandNode<CommandSource> target, final String alias) {
    // Brigadier parses command input within different contexts. When a node with a redirect is
    // reached, a child context builder with the redirect target set as its root node is created.
    // Command implementations are interested in knowing the used alias, i.e. the name of the
    // redirect origin. A child context does not know about its origin, so we cannot use
    // the redirect system as is.
    // We could use a redirect modifier to inject the origin node into the child context, but
    // that would leave the context in an invalid state.
    Preconditions.checkNotNull(target, "target");
    Preconditions.checkNotNull(alias, "alias");
    return LiteralArgumentBuilder
            .<CommandSource>literal(alias)
            .requires(target.getRequirement())
            .requiresWithContext(target.getContextRequirement())
            .executes(target.getCommand())
            .build();
  }

  /**
   * Returns an arguments node builder for a command represented by the given fully-built
   * alias node.
   *
   * @param type the type class of the arguments
   * @param aliasNode the command alias node
   * @param <V> the type of the arguments
   * @return the arguments node builder
   */
  static <V> RequiredArgumentBuilder<CommandSource, V> argumentBuilder(
          final ArgumentType<V> type, final LiteralCommandNode<CommandSource> aliasNode) {
    Preconditions.checkNotNull(type, "type");
    Preconditions.checkNotNull(aliasNode, "aliasNode");
    return RequiredArgumentBuilder
            .<CommandSource, V>argument(ARGS_NODE_NAME, type)
            .executes(aliasNode.getCommand());
  }

  // Hinting

  /**
   * Returns a node to use for hinting the arguments of a command. Hint nodes are sent to
   * 1.13+ clients, and the proxy uses them for providing suggestions.
   *
   * <p>A hint node is able to provide suggestions if and only if the requirements of
   * the corresponding arguments node pass.
   *
   * @param hint the node containing hinting metadata
   * @return the hinting command node
   * @throws IllegalArgumentException if the hinting node is executable or has a redirect
   */
  static CommandNode<CommandSource> createHintingNode(final CommandNode<CommandSource> hint) {
    Preconditions.checkNotNull(hint, "hint");
    if (hint.getCommand() != null) {
      throw new IllegalArgumentException("Cannot use an executable node for hinting");
    }
    if (hint.getRedirect() != null) {
      throw new IllegalArgumentException("Cannot use a node with a redirect for hinting");
    }
    ArgumentBuilder<CommandSource, ?> builder = hint.createBuilder()
            // Requirement checking is performed by SuggestionsProvider
            .requires(source -> false);
    for (final CommandNode<CommandSource> child : hint.getChildren()) {
      builder.then(createHintingNode(child));
    }
    return builder.build();
  }

  private VelocityCommands() {
    throw new AssertionError();
  }
}

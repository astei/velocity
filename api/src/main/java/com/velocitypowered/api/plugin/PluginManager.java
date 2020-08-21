package com.velocitypowered.api.plugin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Manages plugins loaded on the proxy. This manager can retrieve {@link PluginContainer}s from
 * plugin instances and inject arbitrary JAR files into the plugin classpath with {@link
 * #addToClasspath(Object, Path)}.
 */
public interface PluginManager {

  /**
   * Gets the plugin container from an instance.
   *
   * @param instance the instance
   * @return the container
   */
  Optional<PluginContainer> fromInstance(Object instance);

  /**
   * Retrieves a {@link PluginContainer} based on its ID.
   *
   * @param id the plugin ID
   * @return the plugin, if available
   */
  Optional<PluginContainer> getPlugin(String id);

  /**
   * Gets a {@link Collection} of all {@link PluginContainer}s.
   *
   * @return the plugins
   */
  Collection<PluginContainer> getPlugins();

  /**
   * Checks if a plugin is loaded based on its ID.
   *
   * @param id the id of the plugin
   * @return {@code true} if loaded
   */
  boolean isLoaded(String id);

  /**
   * Adds the specified {@code path} to the plugin classpath.
   *
   * @param plugin the plugin
   * @param path the path to the JAR you want to inject into the classpath
   * @throws UnsupportedOperationException if the operation is not applicable to this plugin
   */
  void addToClasspath(Object plugin, Path path);

  /**
   * Plugins folder which stores all the plugins data.
   *
   * @return path to the plugins folder.
   */
  Path getPluginsFolder();
}

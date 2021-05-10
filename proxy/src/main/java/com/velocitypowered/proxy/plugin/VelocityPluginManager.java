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

package com.velocitypowered.proxy.plugin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer;
import com.velocitypowered.proxy.plugin.loader.java.JavaPluginLoader;
import com.velocitypowered.proxy.plugin.util.PluginDependencyUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VelocityPluginManager implements PluginManager {

  private static final Logger logger = LogManager.getLogger(VelocityPluginManager.class);

  private final Map<String, PluginContainer> plugins = new LinkedHashMap<>();
  private final Map<Object, PluginContainer> pluginInstances = new IdentityHashMap<>();

  private void registerPlugin(PluginContainer plugin) {
    plugins.put(plugin.getDescription().getId(), plugin);
    Optional<?> instance = plugin.getInstance();
    instance.ifPresent(o -> pluginInstances.put(o, plugin));
  }

  /**
   * Loads all plugins from the specified {@code directory}.
   * @param directory the directory to load from
   * @param server the proxy server
   * @throws IOException if we could not open the directory
   */
  @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
      justification = "I looked carefully and there's no way SpotBugs is right.")
  public void loadPlugins(Path directory, VelocityServer server) throws IOException {
    checkNotNull(directory, "directory");
    checkArgument(directory.toFile().isDirectory(), "provided path isn't a directory");

    List<PluginDescription> found = new ArrayList<>();
    JavaPluginLoader loader = new JavaPluginLoader(server, directory);

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory,
        p -> p.toFile().isFile() && p.toString().endsWith(".jar"))) {
      for (Path path : stream) {
        try {
          found.add(loader.loadPluginDescription(path));
        } catch (Exception e) {
          logger.error("Unable to load plugin {}", path, e);
        }
      }
    }

    if (found.isEmpty()) {
      // No plugins found
      return;
    }

    List<PluginDescription> sortedPlugins = PluginDependencyUtils.sortCandidates(found);

    Set<String> loadedPluginsById = new HashSet<>();
    Map<PluginContainer, Module> pluginContainers = new LinkedHashMap<>();
    // Now load the plugins
    pluginLoad:
    for (PluginDescription candidate : sortedPlugins) {
      // Verify dependencies
      for (PluginDependency dependency : candidate.getDependencies()) {
        if (!dependency.isOptional() && !loadedPluginsById.contains(dependency.getId())) {
          logger.error("Can't load plugin {} due to missing dependency {}", candidate.getId(),
              dependency.getId());
          continue pluginLoad;
        }
      }

      try {
        PluginDescription realPlugin = loader.loadPlugin(candidate);
        VelocityPluginContainer container = new VelocityPluginContainer(realPlugin);
        pluginContainers.put(container, loader.createModule(container));
        loadedPluginsById.add(realPlugin.getId());
      } catch (Exception e) {
        logger.error("Can't create module for plugin {}", candidate.getId(), e);
      }
    }

    // Make a global Guice module that with common bindings for every plugin
    AbstractModule commonModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(ProxyServer.class).toInstance(server);
        bind(PluginManager.class).toInstance(server.getPluginManager());
        bind(EventManager.class).toInstance(server.getEventManager());
        bind(CommandManager.class).toInstance(server.getCommandManager());
        for (PluginContainer container : pluginContainers.keySet()) {
          bind(PluginContainer.class)
            .annotatedWith(Names.named(container.getDescription().getId()))
            .toInstance(container);
        }
      }
    };

    for (Map.Entry<PluginContainer, Module> plugin : pluginContainers.entrySet()) {
      PluginContainer container = plugin.getKey();
      PluginDescription description = container.getDescription();

      try {
        loader.createPlugin(container, plugin.getValue(), commonModule);
      } catch (Exception e) {
        logger.error("Can't create plugin {}", description.getId(), e);
        continue;
      }

      logger.info("Loaded plugin {} {} by {}", description.getId(), description.getVersion()
          .orElse("<UNKNOWN>"), Joiner.on(", ").join(description.getAuthors()));
      registerPlugin(container);
    }
  }

  @Override
  public Optional<PluginContainer> fromInstance(Object instance) {
    checkNotNull(instance, "instance");

    if (instance instanceof PluginContainer) {
      return Optional.of((PluginContainer) instance);
    }

    return Optional.ofNullable(pluginInstances.get(instance));
  }

  @Override
  public Optional<PluginContainer> getPlugin(String id) {
    checkNotNull(id, "id");
    return Optional.ofNullable(plugins.get(id));
  }

  @Override
  public Collection<PluginContainer> getPlugins() {
    return Collections.unmodifiableCollection(plugins.values());
  }

  @Override
  public boolean isLoaded(String id) {
    return plugins.containsKey(id);
  }

  @Override
  public void addToClasspath(Object plugin, Path path) {
    checkNotNull(plugin, "instance");
    checkNotNull(path, "path");
    Optional<PluginContainer> optContainer = fromInstance(plugin);
    checkArgument(optContainer.isPresent(), "plugin is not loaded");
    Optional<?> optInstance = optContainer.get().getInstance();
    checkArgument(optInstance.isPresent(), "plugin has no instance");

    ClassLoader pluginClassloader = optInstance.get().getClass().getClassLoader();
    if (pluginClassloader instanceof PluginClassLoader) {
      ((PluginClassLoader) pluginClassloader).addPath(path);
    } else {
      throw new UnsupportedOperationException(
          "Operation is not supported on non-Java Velocity plugins.");
    }
  }
}

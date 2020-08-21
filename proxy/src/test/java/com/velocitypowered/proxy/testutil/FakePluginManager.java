package com.velocitypowered.proxy.testutil;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.NonNull;

public class FakePluginManager implements PluginManager {

  public static final Object PLUGIN_A = new Object();
  public static final Object PLUGIN_B = new Object();

  private static final PluginContainer PC_A = new FakePluginContainer("a", PLUGIN_A);
  private static final PluginContainer PC_B = new FakePluginContainer("b", PLUGIN_B);

  @Override
  public @NonNull Optional<PluginContainer> fromInstance(@NonNull Object instance) {
    if (instance == PLUGIN_A) {
      return Optional.of(PC_A);
    } else if (instance == PLUGIN_B) {
      return Optional.of(PC_B);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public @NonNull Optional<PluginContainer> getPlugin(@NonNull String id) {
    switch (id) {
      case "a":
        return Optional.of(PC_A);
      case "b":
        return Optional.of(PC_B);
      default:
        return Optional.empty();
    }
  }

  @Override
  public @NonNull Collection<PluginContainer> getPlugins() {
    return ImmutableList.of(PC_A, PC_B);
  }

  @Override
  public boolean isLoaded(@NonNull String id) {
    return id.equals("a") || id.equals("b");
  }

  @Override
  public void addToClasspath(@NonNull Object plugin, @NonNull Path path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path getPluginsFolder() {
    return Paths.get("plugins");
  }

  private static class FakePluginContainer implements PluginContainer {

    private final String id;
    private final Object instance;

    private FakePluginContainer(String id, Object instance) {
      this.id = id;
      this.instance = instance;
    }

    @Override
    public @NonNull PluginDescription getDescription() {
      return () -> id;
    }

    @Override
    public Optional<?> getInstance() {
      return Optional.of(instance);
    }
  }
}

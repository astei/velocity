package com.velocitypowered.proxy.plugin.loader.java;

import static com.google.common.base.Preconditions.checkNotNull;

import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginDescription;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

class JavaVelocityPluginDescription extends VelocityPluginDescription {

  private final Class<?> mainClass;

  JavaVelocityPluginDescription(String id, @Nullable String name, @Nullable String version,
      @Nullable String description, @Nullable String url,
      @Nullable List<String> authors, Collection<PluginDependency> dependencies, Path source,
      Path dataFolder, Class<?> mainClass) {
    super(id, name, version, description, url, authors, dependencies, source, dataFolder);
    this.mainClass = checkNotNull(mainClass);
  }

  Class<?> getMainClass() {
    return mainClass;
  }
}

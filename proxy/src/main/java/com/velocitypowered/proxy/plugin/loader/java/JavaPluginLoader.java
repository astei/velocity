package com.velocitypowered.proxy.plugin.loader.java;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.velocitypowered.api.plugin.InvalidPluginException;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.ap.SerializedPluginDescription;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.PluginClassLoader;
import com.velocitypowered.proxy.plugin.loader.PluginLoader;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginContainer;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginDescription;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class JavaPluginLoader implements PluginLoader {

  private final ProxyServer server;
  private final Path baseDirectory;

  public JavaPluginLoader(ProxyServer server, Path baseDirectory) {
    this.server = server;
    this.baseDirectory = baseDirectory;
  }

  @Override
  public PluginDescription loadPluginDescription(Path source) throws Exception {
    Optional<SerializedPluginDescription> serialized = getSerializedPluginInfo(source);

    if (!serialized.isPresent()) {
      throw new InvalidPluginException("Did not find a valid velocity-plugin.json.");
    }

    SerializedPluginDescription pd = serialized.get();
    if (!SerializedPluginDescription.ID_PATTERN.matcher(pd.getId()).matches()) {
      throw new InvalidPluginException("Plugin ID '" + pd.getId() + "' is invalid.");
    }

    return createCandidateDescription(pd, source);
  }

  @Override
  public PluginDescription loadPlugin(PluginDescription source) throws Exception {
    if (!(source instanceof JavaVelocityPluginDescriptionCandidate)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }
    PluginClassLoader loader = new PluginClassLoader(
        new URL[]{source.getSource().get().toUri().toURL()}
    );
    loader.addToClassloaders();

    JavaVelocityPluginDescriptionCandidate candidate =
        (JavaVelocityPluginDescriptionCandidate) source;
    Class mainClass = loader.loadClass(candidate.getMainClass());
    return createDescription(candidate, mainClass);
  }

  @Override
  public Module createModule(PluginContainer container) throws Exception {
    PluginDescription description = container.getDescription();
    if (!(description instanceof JavaVelocityPluginDescription)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    JavaVelocityPluginDescription javaDescription = (JavaVelocityPluginDescription) description;
    Optional<Path> source = javaDescription.getSource();

    if (!source.isPresent()) {
      throw new IllegalArgumentException("No path in plugin description");
    }

    return new VelocityPluginModule(server, javaDescription, container, baseDirectory);
  }

  @Override
  public void createPlugin(PluginContainer container, Module... modules) {
    if (!(container instanceof VelocityPluginContainer)) {
      throw new IllegalArgumentException("Container provided isn't of the Java plugin loader");
    }
    PluginDescription description = container.getDescription();
    if (!(description instanceof JavaVelocityPluginDescription)) {
      throw new IllegalArgumentException("Description provided isn't of the Java plugin loader");
    }

    Injector injector = Guice.createInjector(modules);
    Object instance = injector
        .getInstance(((JavaVelocityPluginDescription) description).getMainClass());

    if (instance == null) {
      throw new IllegalStateException(
        "Got nothing from injector for plugin " + description.getId());
    }

    ((VelocityPluginContainer) container).setInstance(instance);
  }

  private Optional<SerializedPluginDescription> getSerializedPluginInfo(Path source)
      throws Exception {
    boolean foundBungeeBukkitPluginFile = false;
    try (JarInputStream in = new JarInputStream(
        new BufferedInputStream(Files.newInputStream(source)))) {
      JarEntry entry;
      while ((entry = in.getNextJarEntry()) != null) {
        if (entry.getName().equals("velocity-plugin.json")) {
          try (Reader pluginInfoReader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return Optional.of(VelocityServer.GENERAL_GSON.fromJson(pluginInfoReader,
                SerializedPluginDescription.class));
          }
        }

        if (entry.getName().equals("plugin.yml") || entry.getName().equals("bungee.yml")) {
          foundBungeeBukkitPluginFile = true;
        }
      }

      if (foundBungeeBukkitPluginFile) {
        throw new InvalidPluginException("The plugin file " + source.getFileName() + " appears to "
            + "be a Bukkit or BungeeCord plugin. Velocity does not support Bukkit or BungeeCord "
            + "plugins.");
      }

      return Optional.empty();
    }
  }

  private VelocityPluginDescription createCandidateDescription(
      SerializedPluginDescription description,
      Path source) {
    Set<PluginDependency> dependencies = new HashSet<>();

    for (SerializedPluginDescription.Dependency dependency : description.getDependencies()) {
      dependencies.add(toDependencyMeta(dependency));
    }

    final Path dataFolder = Paths.get("plugins", description.getId());

    return new JavaVelocityPluginDescriptionCandidate(
        description.getId(),
        description.getName(),
        description.getVersion(),
        description.getDescription(),
        description.getUrl(),
        description.getAuthors(),
        dependencies,
        source,
        dataFolder,
        description.getMain()
    );
  }

  private VelocityPluginDescription createDescription(
      JavaVelocityPluginDescriptionCandidate description,
      Class mainClass) {
    return new JavaVelocityPluginDescription(
        description.getId(),
        description.getName().orElse(null),
        description.getVersion().orElse(null),
        description.getDescription().orElse(null),
        description.getUrl().orElse(null),
        description.getAuthors(),
        description.getDependencies(),
        description.getSource().orElse(null),
        description.getDataFolder().orElse(null),
        mainClass
    );
  }

  private static PluginDependency toDependencyMeta(
      SerializedPluginDescription.Dependency dependency) {
    return new PluginDependency(
        dependency.getId(),
        null, // TODO Implement version matching in dependency annotation
        dependency.isOptional()
    );
  }
}

package com.velocitypowered.api.plugin;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.velocitypowered.api.plugin.meta.PluginDependency;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents metadata for a specific version of a plugin.
 */
public interface PluginDescription {

  /**
   * The pattern plugin IDs must match. Plugin IDs may only contain alphanumeric characters, dashes
   * or underscores, must start with an alphabetic character and cannot be longer than 64
   * characters.
   */
  Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{0,63}");

  /**
   * Gets the qualified ID of the {@link Plugin} within this container.
   *
   * @return the plugin ID
   * @see Plugin#id()
   */
  String getId();

  /**
   * Gets the name of the {@link Plugin} within this container.
   *
   * @return an {@link Optional} with the plugin name, may be empty
   * @see Plugin#name()
   */
  default Optional<String> getName() {
    return Optional.empty();
  }

  /**
   * Gets the version of the {@link Plugin} within this container.
   *
   * @return an {@link Optional} with the plugin version, may be empty
   * @see Plugin#version()
   */
  default Optional<String> getVersion() {
    return Optional.empty();
  }

  /**
   * Gets the description of the {@link Plugin} within this container.
   *
   * @return an {@link Optional} with the plugin description, may be empty
   * @see Plugin#description()
   */
  default Optional<String> getDescription() {
    return Optional.empty();
  }

  /**
   * Gets the url or website of the {@link Plugin} within this container.
   *
   * @return an {@link Optional} with the plugin url, may be empty
   * @see Plugin#url()
   */
  default Optional<String> getUrl() {
    return Optional.empty();
  }

  /**
   * Gets the authors of the {@link Plugin} within this container.
   *
   * @return the plugin authors, may be empty
   * @see Plugin#authors()
   */
  default List<String> getAuthors() {
    return ImmutableList.of();
  }

  /**
   * Gets a {@link Collection} of all dependencies of the {@link Plugin} within this container.
   *
   * @return the plugin dependencies, can be empty
   * @see Plugin#dependencies()
   */
  default Collection<PluginDependency> getDependencies() {
    return ImmutableSet.of();
  }

  default Optional<PluginDependency> getDependency(String id) {
    return Optional.empty();
  }

  /**
   * Returns the source the plugin was loaded from.
   *
   * @return the source the plugin was loaded from or {@link Optional#empty()} if unknown
   */
  default Optional<Path> getSource() {
    return Optional.empty();
  }

  /**
   * Returns the data folder for the plugin.
   *
   * @return the data folder for plugin to store data or {@link Optional#empty()} if unknown
   */
  default Optional<Path> getDataFolder() {
    return Optional.empty();
  }

  /**
   * Returns the data folder as File.
   *
   * @return File that represents the provided Path or {@link Optional#empty()} if unknown
   */
  default Optional<File> getDataFolderAsFile() {
    final Optional<Path> dataFolder = getDataFolder();
    return dataFolder.map(Path::toFile);
  }
}

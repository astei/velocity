package com.velocitypowered.proxy.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.velocitypowered.api.service.ServiceManager;
import com.velocitypowered.api.service.ServiceProvider;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VelocityServiceManager implements ServiceManager {

  private final VelocityPluginManager pluginManager;
  private final ConcurrentMap<Class<?>, ServiceProvider<?>>
          registeredServices = new ConcurrentHashMap<>();

  /**
   * Initalizes the service manager.
   *
   * @param pluginManager the Velocity plugin manager
   */
  public VelocityServiceManager(VelocityPluginManager pluginManager) {
    this.pluginManager = pluginManager;
  }

  @Override
  public <S> boolean register(Object plugin, Class<S> serviceClass, S provider) {
    checkNotNull(plugin, "plugin");
    checkNotNull(serviceClass, "serviceClass");
    checkNotNull(provider, "provider");
    checkArgument(pluginManager.fromInstance(plugin).isPresent(), "plugin is not registered");

    VelocityServiceProvider<S> wrapper =
            new VelocityServiceProvider<>(plugin, serviceClass, provider);

    return registeredServices.putIfAbsent(serviceClass, wrapper) == null;
  }

  @Override
  public <S> Optional<S> getService(Class<S> serviceClass) {
    checkNotNull(serviceClass, "serviceClass");

    ServiceProvider<?> provider = registeredServices.get(serviceClass);
    if (provider == null) {
      return Optional.empty();
    }
    checkArgument(serviceClass.equals(provider.getService()), "illegal service provider");

    return Optional.of(serviceClass.cast(provider.getProvider()));
  }

  @Override
  public boolean isProvided(Class<?> serviceClass) {
    return registeredServices.containsKey(serviceClass);
  }
}

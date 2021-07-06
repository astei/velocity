package com.velocitypowered.proxy.service;

import com.velocitypowered.api.service.ServiceProvider;

public class VelocityServiceProvider<S> implements ServiceProvider<S> {

  private final Object plugin;
  private final Class<S> serviceClass;
  private final S provider;

  /**
   * Initalizes the service provider.
   *
   * @param plugin the providing plugin
   * @param serviceClass the provided service
   * @param provider the service provider
   */
  VelocityServiceProvider(Object plugin, Class<S> serviceClass, S provider) {
    this.plugin = plugin;
    this.serviceClass = serviceClass;
    this.provider = provider;
  }

  @Override
  public Object getPlugin() {
    return plugin;
  }

  @Override
  public Class<S> getService() {
    return serviceClass;
  }

  @Override
  public S getProvider() {
    return provider;
  }
}

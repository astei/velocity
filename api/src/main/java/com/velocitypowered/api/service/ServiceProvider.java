package com.velocitypowered.api.service;

/**
 * Represents a registered service provider.
 * */
public interface ServiceProvider<S> {

  /**
   * Get the plugin providing the service.
   *
   * @return the providing plugin
   * */
  Object getPlugin();

  /**
   * Get the service being provided.
   *
   * @return the provided service
   * */
  Class<S> getService();

  /**
   * Get the service provider.
   *
   * @return the service provider
   * */
  S getProvider();
}

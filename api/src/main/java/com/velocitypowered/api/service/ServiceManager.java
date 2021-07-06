package com.velocitypowered.api.service;

import java.util.Optional;

/**
 * Manages services registered by plugins.
 * */
public interface ServiceManager {

  /**
  * Register a service provider.
  *
  * @param plugin the plugin registering the provider
  * @param serviceClass the service being provided
  * @param provider the provider
  *
  * @return {@code true} if we registered the provided, {@code false} if not
  * */
  <S> boolean register(Object plugin, Class<S> serviceClass, S provider);

  /**
  * Get a registered service.
  *
  * @param serviceClass the service to be provided
  *
  * @return the provider, which may be empty
  * */
  <S> Optional<S> getService(Class<S> serviceClass);

  /**
  * Check if a service is registered.
  *
  * @param serviceClass the service to be checked
  *
  * @return {@code true} if the service is provided, {@code false} if not
  * */
  boolean isProvided(Class<?> serviceClass);
}

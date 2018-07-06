package org.batfish.datamodel;

import java.util.Map;
import javax.annotation.Nonnull;

public abstract class DataPlaneBuilder<B extends DataPlaneBuilder<B, T>, T> {

  protected Map<String, Configuration> _configurations;

  public abstract @Nonnull T build();

  public abstract @Nonnull B getThis();

  public @Nonnull B setConfigurations(@Nonnull Map<String, Configuration> configurations) {
    _configurations = configurations;
    return getThis();
  }
}

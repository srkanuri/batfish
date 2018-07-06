package org.batfish.common.plugin;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import java.util.Map;
import java.util.function.Function;
import org.batfish.common.SerializableAsMessage;

public abstract class Plugin implements Comparable<Plugin> {

  protected PluginConsumer _pluginConsumer;

  @Override
  public final int compareTo(Plugin o) {
    return getClass().getCanonicalName().compareTo(o.getClass().getCanonicalName());
  }

  public Map<String, Function<Message, SerializableAsMessage<? extends Message>>> getConverters() {
    return ImmutableMap.of();
  }

  public Map<String, Descriptor> getDescriptors() {
    return ImmutableMap.of();
  }

  @Override
  public final boolean equals(Object obj) {
    return obj != null && getClass().equals(obj.getClass());
  }

  @Override
  public final int hashCode() {
    return getClass().hashCode();
  }

  public final void initialize(PluginConsumer pluginConsumer) {
    _pluginConsumer = pluginConsumer;
    pluginConsumer.getConverters().putAll(getConverters());
    pluginConsumer.getDescriptors().putAll(getDescriptors());
    pluginInitialize();
  }

  protected abstract void pluginInitialize();
}

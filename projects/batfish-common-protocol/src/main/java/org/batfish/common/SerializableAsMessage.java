package org.batfish.common;

import com.google.protobuf.Message;
import javax.annotation.Nonnull;

public interface SerializableAsMessage<MessageT extends Message> {

  @Nonnull
  MessageT toMessage();
}

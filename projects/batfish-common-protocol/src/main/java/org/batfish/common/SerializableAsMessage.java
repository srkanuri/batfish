package org.batfish.common;

import com.google.protobuf.Message;

public interface SerializableAsMessage<MessageT extends Message> {

  MessageT toMessage();
}

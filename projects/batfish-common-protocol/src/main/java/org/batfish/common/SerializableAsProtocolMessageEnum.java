package org.batfish.common;

import com.google.protobuf.ProtocolMessageEnum;
import javax.annotation.Nonnull;

public interface SerializableAsProtocolMessageEnum<
    ProtocolMessageEnumT extends ProtocolMessageEnum> {
  @Nonnull
  ProtocolMessageEnumT toProtocolMessageEnum();
}

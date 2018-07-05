package org.batfish.datamodel;

import javax.annotation.Nonnull;
import org.batfish.common.BatfishException;
import org.batfish.common.SerializableAsProtocolMessageEnum;

public enum IsisLevel implements SerializableAsProtocolMessageEnum<IsisLevelOuterClass.IsisLevel> {
  LEVEL_1,
  LEVEL_1_2,
  LEVEL_2;

  public static @Nonnull IsisLevel fromProtocolMessageEnum(
      @Nonnull IsisLevelOuterClass.IsisLevel isisLevel) {
    switch (isisLevel) {
      case IsisLevel_LEVEL_1:
        return LEVEL_1;
      case IsisLevel_LEVEL_1_2:
        return LEVEL_1_2;
      case IsisLevel_LEVEL_2:
        return LEVEL_2;
      case UNRECOGNIZED:
      default:
        throw new BatfishException(String.format("Invalid IsisLevel: %s", isisLevel));
    }
  }

  @Override
  public IsisLevelOuterClass.IsisLevel toProtocolMessageEnum() {
    switch (this) {
      case LEVEL_1:
        return IsisLevelOuterClass.IsisLevel.IsisLevel_LEVEL_1;
      case LEVEL_1_2:
        return IsisLevelOuterClass.IsisLevel.IsisLevel_LEVEL_1_2;
      case LEVEL_2:
        return IsisLevelOuterClass.IsisLevel.IsisLevel_LEVEL_2;
      default:
        throw new BatfishException(String.format("Invalid IsisLevel: %s", this));
    }
  }
}

package org.batfish.datamodel.ospf;

import javax.annotation.Nonnull;
import org.batfish.common.BatfishException;
import org.batfish.common.SerializableAsProtocolMessageEnum;
import org.batfish.datamodel.OspfMetricTypeOuterClass;
import org.batfish.datamodel.RoutingProtocol;

public enum OspfMetricType
    implements SerializableAsProtocolMessageEnum<OspfMetricTypeOuterClass.OspfMetricType> {
  E1,
  E2;

  public static OspfMetricType fromInteger(int i) {
    switch (i) {
      case 1:
        return E1;
      case 2:
        return E2;
      default:
        throw new BatfishException("invalid ospf metric type");
    }
  }

  public static @Nonnull OspfMetricType fromProtocolMessageEnum(
      OspfMetricTypeOuterClass.OspfMetricType ospfMetricType) {
    switch (ospfMetricType) {
      case OspfMetricType_E1:
        return E1;
      case OspfMetricType_E2:
        return E2;
      case UNRECOGNIZED:
      default:
        throw new BatfishException(String.format("Invalid OspfMetricType: %s", ospfMetricType));
    }
  }

  @Override
  public OspfMetricTypeOuterClass.OspfMetricType toProtocolMessageEnum() {
    switch (this) {
      case E1:
        return OspfMetricTypeOuterClass.OspfMetricType.OspfMetricType_E1;
      case E2:
        return OspfMetricTypeOuterClass.OspfMetricType.OspfMetricType_E2;
      default:
        throw new BatfishException(String.format("Invalid OspfMetricType: %s", this));
    }
  }

  public RoutingProtocol toRoutingProtocol() {
    switch (this) {
      case E1:
        return RoutingProtocol.OSPF_E1;
      case E2:
        return RoutingProtocol.OSPF_E2;
      default:
        throw new BatfishException("invalid ospf metric type");
    }
  }
}

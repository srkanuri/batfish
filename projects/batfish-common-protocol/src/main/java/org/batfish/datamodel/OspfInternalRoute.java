package org.batfish.datamodel;

import java.util.Objects;
import javax.annotation.Nonnull;

public abstract class OspfInternalRoute extends OspfRoute {

  /** */
  private static final long serialVersionUID = 1L;

  public static class Builder extends AbstractRouteBuilder<Builder, OspfInternalRoute> {

    private Long _area;

    private RoutingProtocol _protocol;

    @Override
    public OspfInternalRoute build() {
      if (_protocol == RoutingProtocol.OSPF) {
        return new OspfIntraAreaRoute(getNetwork(), getNextHopIp(), getAdmin(), getMetric(), _area);
      } else {
        return new OspfInterAreaRoute(getNetwork(), getNextHopIp(), getAdmin(), getMetric(), _area);
      }
    }

    @Override
    protected Builder getThis() {
      return this;
    }

    public Builder setArea(Long area) {
      _area = area;
      return this;
    }

    public Builder setProtocol(RoutingProtocol protocol) {
      _protocol = protocol;
      return this;
    }
  }

  public OspfInternalRoute(Prefix network, Ip nextHopIp, int admin, long metric, long area) {
    super(network, nextHopIp, admin, metric, area);
  }

  @Override
  protected final String protocolRouteString() {
    return " area:" + _area;
  }

  @Override
  public final int hashCode() {
    return Objects.hash(_admin, _area, _metric, _network, _nextHopIp);
  }

  @Override
  protected final RouteOuterClass.Route completeMessage(
      @Nonnull RouteOuterClass.Route.Builder routeBuilder) {
    return routeBuilder
        .setOspfRoute(
            OspfRouteOuterClass.OspfRoute.newBuilder()
                .setAdministrativeDistance(_admin)
                .setArea(_area)
                .setInternal(
                    OspfInternalRouteOuterClass.OspfInternalRoute.newBuilder()
                        .setProtocol(getProtocol().toProtocolMessageEnum())
                        .build())
                .setMetric(_metric)
                .setNextHopIp(_nextHopIp.toString())
                .build())
        .build();
  }

  public static @Nonnull Builder fromOspfInternalRoute(@Nonnull RouteOuterClass.Route message) {
    OspfRouteOuterClass.OspfRoute ospfRoute = message.getOspfRoute();
    OspfInternalRouteOuterClass.OspfInternalRoute internal = message.getOspfRoute().getInternal();
    return new Builder()
        .setAdmin(ospfRoute.getAdministrativeDistance())
        .setArea(ospfRoute.getArea())
        .setMetric(ospfRoute.getMetric())
        .setNextHopIp(new Ip(ospfRoute.getNextHopIp()))
        .setProtocol(RoutingProtocol.fromProtocolMessageEnum(internal.getProtocol()));
  }
}

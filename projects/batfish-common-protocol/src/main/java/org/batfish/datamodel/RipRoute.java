package org.batfish.datamodel;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nonnull;
import org.batfish.common.BatfishException;

public abstract class RipRoute extends AbstractRoute {

  public static class Builder extends AbstractRouteBuilder<Builder, RipRoute> {

    @Override
    public RipRoute build() {
      return new RipInternalRoute(getNetwork(), getNextHopIp(), getAdmin(), getMetric());
    }

    @Override
    protected Builder getThis() {
      return this;
    }
  }

  /** */
  private static final long serialVersionUID = 1L;

  /** Maximum allowable route metric in RIP */
  public static final long MAX_ROUTE_METRIC = 16;

  protected final int _admin;

  protected final long _metric;

  protected final Ip _nextHopIp;

  @JsonCreator
  public RipRoute(
      @JsonProperty(PROP_NETWORK) Prefix network,
      @JsonProperty(PROP_NEXT_HOP_IP) Ip nextHopIp,
      @JsonProperty(PROP_ADMINISTRATIVE_COST) int admin,
      @JsonProperty(PROP_METRIC) long metric) {
    super(network);
    if (metric < 0 || metric > MAX_ROUTE_METRIC) {
      throw new BatfishException(
          String.format(
              "Invalid RIP route metric %d. Must be between 0 and %d", metric, MAX_ROUTE_METRIC));
    }
    _admin = admin;
    _metric = metric;
    _nextHopIp = firstNonNull(nextHopIp, Route.UNSET_ROUTE_NEXT_HOP_IP);
  }

  @JsonIgnore(false)
  @JsonProperty(PROP_ADMINISTRATIVE_COST)
  @Override
  public final int getAdministrativeCost() {
    return _admin;
  }

  @JsonIgnore(false)
  @JsonProperty(PROP_METRIC)
  @Override
  public final Long getMetric() {
    return _metric;
  }

  @Nonnull
  @JsonIgnore(false)
  @JsonProperty(PROP_NEXT_HOP_IP)
  @Override
  public final Ip getNextHopIp() {
    return _nextHopIp;
  }

  @Override
  protected final RouteOuterClass.Route completeMessage(
      RouteOuterClass.Route.Builder routeBuilder) {
    return routeBuilder
        .setRipRoute(
            RipRouteOuterClass.RipRoute.newBuilder()
                .setAdministrativeDistance(_admin)
                .setMetric(_metric)
                .setNextHopIp(_nextHopIp.toString()))
        .build();
  }

  public static Builder fromRipRoute(RouteOuterClass.Route message) {
    RipRouteOuterClass.RipRoute ripRoute = message.getRipRoute();
    return new Builder()
        .setAdmin(ripRoute.getAdministrativeDistance())
        .setMetric(ripRoute.getMetric())
        .setNextHopIp(new Ip(ripRoute.getNextHopIp()));
  }
}

package org.batfish.dataplane.ibdp;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Table;
import com.google.common.graph.Network;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.AbstractRoute;
import org.batfish.datamodel.BgpPeerConfig;
import org.batfish.datamodel.BgpRoute;
import org.batfish.datamodel.BgpSession;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.DataPlaneBuilder;
import org.batfish.datamodel.DataPlaneOuterClass;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.Fib;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.GenericRib;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.Topology;
import org.batfish.dataplane.ibdp.IncrementalDataPlane.Builder;
import org.batfish.dataplane.rib.Rib;

public class CompletedIncrementalDataPlane implements DataPlane {

  public static class Builder extends DataPlaneBuilder<Builder, CompletedIncrementalDataPlane> {

    public static CompletedIncrementalDataPlane fromMessage(Message message) {
      DataPlaneOuterClass.DataPlane dpMessage = (DataPlaneOuterClass.DataPlane) message;
      IncrementalDataPlaneOuterClass.IncrementalDataPlane ibdpMessage;
      SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> ribs = new TreeMap<>();
      Builder builder = builder();
      try {
        ibdpMessage =
            dpMessage.getImpl().unpack(IncrementalDataPlaneOuterClass.IncrementalDataPlane.class);
      } catch (InvalidProtocolBufferException e) {
        throw new BatfishException("Could not unpack incremental data plane", e);
      }
      ibdpMessage
          .getMainRibsList()
          .forEach(
              mainRibMessage -> {
                Rib rib =
                    (Rib)
                        ribs.computeIfAbsent(mainRibMessage.getHostname(), h -> new TreeMap<>())
                            .computeIfAbsent(mainRibMessage.getVrfName(), v -> new Rib());
              });
      IncrementalDataPlane ibdp = new IncrementalDataPlane(builder);
    }

    private SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> _ribs;

    @Override
    public CompletedIncrementalDataPlane build() {
      return new CompletedIncrementalDataPlane(_configurations, _ribs);
    }

    @Override
    public Builder getThis() {
      return this;
    }
  }

  private final SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> _ribs;

  private transient Map<String, Configuration> _configurations;

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  public CompletedIncrementalDataPlane(
      Map<String, Configuration> configurations,
      SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> ribs) {
    _configurations = configurations;
    _ribs = ribs;
  }

  @Override
  public DataPlaneOuterClass.DataPlane toMessage() {
    IncrementalDataPlaneOuterClass.IncrementalDataPlane.Builder implBuilder =
        IncrementalDataPlaneOuterClass.IncrementalDataPlane.newBuilder();
    _ribs.forEach(
        (hostname, ribsByVrf) ->
            ribsByVrf.forEach(
                (vrfName, rib) -> {
                  RibOuterClass.Rib.Builder ribBuilder = RibOuterClass.Rib.newBuilder();
                  ribBuilder.setHostname(hostname);
                  ribBuilder.setVrfName(vrfName);
                  rib.getRoutes().forEach(route -> ribBuilder.addRoutes(route.toMessage()));
                  implBuilder.addMainRibs(ribBuilder.build());
                }));
    return DataPlaneOuterClass.DataPlane.newBuilder()
        .setImpl(Any.pack(implBuilder.build()))
        .build();
  }

  public CompletedIncrementalDataPlane(IncrementalDataPlane incrementalDataPlane) {
    // TODO Auto-generated constructor stub
  }

  @Override
  public Table<String, String, Set<BgpRoute>> getBgpRoutes(boolean multipath) {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  public Network<BgpPeerConfig, BgpSession> getBgpTopology() {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  public Map<String, Configuration> getConfigurations() {
    throw new UnsupportedOperationException(
        "no implementation for generated method"); // TODO Auto-generated method stub
  }

  @Override
  public Map<String, Map<String, Fib>> getFibs() {
    return _fibs.get();
  }

  @Override
  public ForwardingAnalysis getForwardingAnalysis() {
    return _forwardingAnalysis.get();
  }

  @Override
  public Map<Ip, Set<String>> getIpOwners() {
    return _ipOwners.get();
  }

  @Override
  public Map<Ip, Map<String, Set<String>>> getIpVrfOwners() {
    return _ipVrfOwners.get();
  }

  @Override
  public SortedMap<String, SortedMap<String, GenericRib<AbstractRoute>>> getRibs() {
    return _ribs;
  }

  @Override
  public Topology getTopology() {
    return _topology.get();
  }

  @Override
  public SortedSet<Edge> getTopologyEdges() {
    return _topologyEdges.get();
  }

  @Override
  public SortedMap<String, SortedMap<String, Map<Prefix, Map<String, Set<String>>>>>
      getPrefixTracingInfoSummary() {
    return ImmutableSortedMap.of();
  }
}

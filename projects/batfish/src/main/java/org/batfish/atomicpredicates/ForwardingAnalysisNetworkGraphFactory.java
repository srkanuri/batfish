package org.batfish.atomicpredicates;

import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpSpace;
import org.batfish.main.BDDUtils;
import org.batfish.specifier.InterfaceLinkLocation;
import org.batfish.specifier.InterfaceLocation;
import org.batfish.specifier.IpSpaceAssignment;
import org.batfish.specifier.Location;
import org.batfish.specifier.LocationVisitor;
import org.batfish.symbolic.bdd.AtomicPredicates;
import org.batfish.symbolic.bdd.BDDAcl;
import org.batfish.symbolic.bdd.BDDOps;
import org.batfish.symbolic.bdd.BDDPacket;
import org.batfish.symbolic.bdd.IpSpaceToBDD;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.Accept;
import org.batfish.z3.state.Drop;
import org.batfish.z3.state.NeighborUnreachable;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeDrop;
import org.batfish.z3.state.NodeDropAclIn;
import org.batfish.z3.state.NodeDropAclOut;
import org.batfish.z3.state.NodeDropNoRoute;
import org.batfish.z3.state.NodeDropNullRoute;
import org.batfish.z3.state.NodeInterfaceNeighborUnreachable;
import org.batfish.z3.state.OriginateInterfaceLink;
import org.batfish.z3.state.OriginateVrf;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreInInterface;
import org.batfish.z3.state.PreOutEdgePostNat;
import org.batfish.z3.state.PreOutVrf;

public class ForwardingAnalysisNetworkGraphFactory {
  private Map<String, Map<String, BDD>> _aclDenyBDDs;
  private Map<String, Map<String, BDD>> _aclPermitBDDs;
  private Map<org.batfish.datamodel.Edge, BDD> _arpTrueEdgeBDDs;
  private final List<BDD> _apBDDs;
  private final Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> _apTransitions;
  private final BDDFactory _bddFactory;
  private final BDDOps _bddOps;
  private final Map<StateExpr, Map<StateExpr, BDD>> _bddTransitions;
  private final BDDUtils _bddUtils;
  private final Map<String, Configuration> _configs;
  private final ForwardingAnalysis _forwardingAnalysis;
  private IpSpaceToBDD _ipSpaceToBDD;
  private final Map<String, Map<String, Map<String, BDD>>> _neighborUnreachableBDDs;
  private final BDD _nonDstIpVars;
  private final Map<String, Map<String, BDD>> _routableBDDs;
  private final Map<String, Map<String, BDD>> _vrfAcceptBDDs;
  private final Map<String, Map<String, BDD>> _vrfNotAcceptBDDs;

  public ForwardingAnalysisNetworkGraphFactory(
      Map<String, Configuration> configs,
      ForwardingAnalysis forwardingAnalysis,
      boolean dstIpOnly) {
    _bddUtils = new BDDUtils(configs, forwardingAnalysis);
    _bddFactory = _bddUtils.getBDDFactory();
    _bddOps = new BDDOps(_bddFactory);
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _ipSpaceToBDD = _bddUtils.getIpSpaceToBDD();

    BDDPacket pkt = new BDDPacket();
    _nonDstIpVars =
        dstIpOnly
            ? _bddOps.and(
                pkt.getTcpAck(),
                pkt.getTcpCwr(),
                pkt.getTcpEce(),
                pkt.getTcpFin(),
                pkt.getTcpPsh(),
                pkt.getTcpRst(),
                pkt.getTcpSyn(),
                _bddOps.and(pkt.getDstPort().getBitvec()),
                _bddOps.and(pkt.getIcmpCode().getBitvec()),
                _bddOps.and(pkt.getIcmpType().getBitvec()),
                _bddOps.and(pkt.getIpProtocol().getBitvec()),
                _bddOps.and(pkt.getSrcIp().getBitvec()),
                _bddOps.and(pkt.getSrcPort().getBitvec()))
            : null;

    Map<String, Map<String, BDDAcl>> bddAcls = computeBDDAcls(configs);
    _aclDenyBDDs = computeAclDenyBDDs(bddAcls, dstIpOnly);
    _aclPermitBDDs = computeAclPermitBDDs(bddAcls, dstIpOnly);

    _arpTrueEdgeBDDs = computeArpTrueEdgeBDDs(forwardingAnalysis, _ipSpaceToBDD);
    _neighborUnreachableBDDs = computeNeighborUnreachableBDDs(forwardingAnalysis, _ipSpaceToBDD);
    _routableBDDs = computeRoutableBDDs(forwardingAnalysis, _ipSpaceToBDD);
    _vrfAcceptBDDs = computeVrfAcceptBDDs(configs, _ipSpaceToBDD);
    _vrfNotAcceptBDDs = computeVrfNotAcceptBDDs(_vrfAcceptBDDs);

    _bddTransitions = computeBDDTransitions();

    _apBDDs = computeAPBDDs();
    _apTransitions = computeAPTransitions();
  }

  private Map<String, Map<String, BDDAcl>> computeBDDAcls(Map<String, Configuration> configs) {
    return toImmutableMap(
        configs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue().getIpAccessLists(),
                Entry::getKey,
                aclEntry -> BDDAcl.create(aclEntry.getValue())));
  }

  private Map<String, Map<String, BDD>> computeAclDenyBDDs(
      Map<String, Map<String, BDDAcl>> aclBDDs, boolean dstIpOnly) {
    return toImmutableMap(
        aclBDDs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                aclEntry -> {
                  BDD bdd = aclEntry.getValue().getBdd().not();
                  return dstIpOnly ? bdd.exist(_nonDstIpVars) : bdd;
                }));
  }

  private Map<String, Map<String, BDD>> computeAclPermitBDDs(
      Map<String, Map<String, BDDAcl>> aclBDDs, boolean dstIpOnly) {
    return toImmutableMap(
        aclBDDs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                aclEntry -> {
                  BDD bdd = aclEntry.getValue().getBdd();
                  return dstIpOnly ? bdd.exist(_nonDstIpVars) : bdd;
                }));
  }

  private List<BDD> computeAPBDDs() {
    List<BDD> preds =
        _bddTransitions
            .values()
            .stream()
            .flatMap(m -> m.values().stream())
            .collect(Collectors.toList());
    long time = System.currentTimeMillis();
    List<BDD> aps = new BDDTrie(preds).atomicPredicates();
    time = System.currentTimeMillis() - time;
    return aps;
  }

  private List<BDD> computeAPBDDs_old() {
    AtomicPredicates atomicPredicates = new AtomicPredicates(_bddUtils.getBDDFactory());
    return ImmutableList.copyOf(
        atomicPredicates.atomize(
            _bddTransitions
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList())));
  }

  private Map<StateExpr, Map<StateExpr, BDD>> computeBDDTransitions() {
    Map<StateExpr, Map<StateExpr, BDD>> bddTransitions = new HashMap<>();

    generateRules()
        .forEach(
            edge ->
                bddTransitions
                    .computeIfAbsent(edge._preState, k -> new HashMap<>())
                    .put(edge._postState, edge._constraint));

    // freeze
    return toImmutableMap(
        bddTransitions,
        Entry::getKey,
        preStateEntry -> toImmutableMap(preStateEntry.getValue(), Entry::getKey, Entry::getValue));
  }

  private static Map<String, Map<String, BDD>> computeRoutableBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getRoutableIps(),
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry -> vrfEntry.getValue().accept(ipSpaceToBDD)));
  }

  private static Map<String, Map<String, BDD>> computeVrfNotAcceptBDDs(
      Map<String, Map<String, BDD>> vrfAcceptBDDs) {
    return toImmutableMap(
        vrfAcceptBDDs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(), Entry::getKey, vrfEntry -> vrfEntry.getValue().not()));
  }

  public List<BDD> getApBDDs() {
    return _apBDDs;
  }

  public Map<StateExpr, Map<StateExpr, BDD>> getBDDTransitions() {
    return _bddTransitions;
  }

  public IpSpaceToBDD getIpSpaceToBDD() {
    return _ipSpaceToBDD;
  }

  Map<String, Map<String, BDD>> getVrfAcceptBDDs() {
    return _vrfAcceptBDDs;
  }

  public Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> getApTransitions() {
    return _apTransitions;
  }

  public BDDFactory getBDDFactory() {
    return _bddFactory;
  }

  @VisibleForTesting
  static class Edge {
    final BDD _constraint;
    final StateExpr _postState;
    final StateExpr _preState;

    Edge(StateExpr preState, StateExpr postState, BDD constraint) {
      _constraint = constraint;
      _postState = postState;
      _preState = preState;
    }
  }

  @VisibleForTesting
  static Map<org.batfish.datamodel.Edge, BDD> computeArpTrueEdgeBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getArpTrueEdge(),
        Entry::getKey,
        entry -> entry.getValue().accept(ipSpaceToBDD));
  }

  @VisibleForTesting
  static Map<String, Map<String, Map<String, BDD>>> computeNeighborUnreachableBDDs(
      ForwardingAnalysis forwardingAnalysis, IpSpaceToBDD ipSpaceToBDD) {
    return toImmutableMap(
        forwardingAnalysis.getNeighborUnreachable(),
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry ->
                    toImmutableMap(
                        vrfEntry.getValue(),
                        Entry::getKey,
                        ifaceEntry -> ifaceEntry.getValue().accept(ipSpaceToBDD))));
  }

  @VisibleForTesting
  Stream<Edge> generateRules() {
    return Streams.concat(
        generateRules_NodeAccept_Accept(),
        generateRules_NodeDropAclIn_NodeDrop(),
        generateRules_NodeDropNoRoute_NodeDrop(),
        generateRules_NodeDropNullRoute_NodeDrop(),
        generateRules_NodeDropAclOut_NodeDrop(),
        generateRules_NodeDrop_Drop(),
        generateRules_NodeInterfaceNeighborUnreachable_NeighborUnreachable(),
        generateRules_OriginateInterface_PreInInterface(),
        generateRules_OriginateVrf_PostInVrf(),
        generateRules_PreInInterface_NodeDropAclIn(),
        generateRules_PreInInterface_PostInVrf(),
        generateRules_PostInVrf_NodeAccept(),
        generateRules_PostInVrf_NodeDropNoRoute(),
        generateRules_PostInVrf_PreOutVrf(),
        generateRules_PreOutEdgePostNat_NodeDropAclOut(),
        generateRules_PreOutEdgePostNat_PreInInterface(),
        generateRules_PreOutVrf_NodeDropNullRoute(),
        generateRules_PreOutVrf_NodeInterfaceNeighborUnreachable(),
        generateRules_PreOutVrf_PreOutEdgePostNat());
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeAccept_Accept() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeAccept(node), Accept.INSTANCE, _bddFactory.one()));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeDropAclIn_NodeDrop() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeDropAclIn(node), new NodeDrop(node), _bddFactory.one()));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeDropAclOut_NodeDrop() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeDropAclOut(node), new NodeDrop(node), _bddFactory.one()));
  }

  private Stream<Edge> generateRules_NodeDropNoRoute_NodeDrop() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeDropNoRoute(node), new NodeDrop(node), _bddFactory.one()));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeDropNullRoute_NodeDrop() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeDropNullRoute(node), new NodeDrop(node), _bddFactory.one()));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeDrop_Drop() {
    return _configs
        .keySet()
        .stream()
        .map(node -> new Edge(new NodeDrop(node), Drop.INSTANCE, _bddFactory.one()));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_NodeInterfaceNeighborUnreachable_NeighborUnreachable() {
    return _configs
        .values()
        .stream()
        .flatMap(c -> c.getInterfaces().values().stream())
        .map(
            iface -> {
              String nodeNode = iface.getOwner().getHostname();
              String ifaceName = iface.getName();
              return new Edge(
                  new NodeInterfaceNeighborUnreachable(nodeNode, ifaceName),
                  NeighborUnreachable.INSTANCE,
                  _bddFactory.one());
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_OriginateInterface_PreInInterface() {
    return _configs
        .values()
        .stream()
        .map(Configuration::getInterfaces)
        .map(Map::values)
        .flatMap(Collection::stream)
        .map(
            iface -> {
              String hostname = iface.getOwner().getHostname();
              String name = iface.getName();
              return new Edge(
                  new OriginateInterfaceLink(hostname, name),
                  new PreInInterface(hostname, name),
                  _bddFactory.one());
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_OriginateVrf_PostInVrf() {
    return _configs
        .values()
        .stream()
        .flatMap(
            config -> {
              String hostname = config.getHostname();
              return config
                  .getVrfs()
                  .values()
                  .stream()
                  .map(
                      vrf -> {
                        String vrfName = vrf.getName();
                        return new Edge(
                            new OriginateVrf(hostname, vrfName),
                            new PostInVrf(hostname, vrfName),
                            _bddFactory.one());
                      });
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PostInVrf_NodeAccept() {
    return _vrfAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD acceptBDD = vrfEntry.getValue();
                          return new Edge(
                              new PostInVrf(node, vrf), new NodeAccept(node), acceptBDD);
                        }));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PostInVrf_NodeDropNoRoute() {
    return _vrfNotAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD notAcceptBDD = vrfEntry.getValue();
                          BDD notRoutableBDD = _routableBDDs.get(node).get(vrf).not();
                          return new Edge(
                              new PostInVrf(node, vrf),
                              new NodeDropNoRoute(node),
                              notAcceptBDD.and(notRoutableBDD));
                        }));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PostInVrf_PreOutVrf() {
    return _vrfNotAcceptBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD notAcceptBDD = vrfEntry.getValue();
                          BDD routableBDD = _routableBDDs.get(node).get(vrf);
                          return new Edge(
                              new PostInVrf(node, vrf),
                              new PreOutVrf(node, vrf),
                              notAcceptBDD.and(routableBDD));
                        }));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreInInterface_NodeDropAclIn() {
    return _configs
        .values()
        .stream()
        .map(Configuration::getVrfs)
        .map(Map::values)
        .flatMap(Collection::stream)
        .flatMap(vrf -> vrf.getInterfaces().values().stream())
        .filter(iface -> iface.getIncomingFilter() != null)
        .map(
            iface -> {
              String aclName = iface.getIncomingFilterName();
              String nodeName = iface.getOwner().getName();
              String ifaceName = iface.getName();

              BDD aclDenyBDD = _aclDenyBDDs.get(nodeName).get(aclName);
              return new Edge(
                  new PreInInterface(nodeName, ifaceName), new NodeDropAclIn(nodeName), aclDenyBDD);
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreInInterface_PostInVrf() {
    return _configs
        .values()
        .stream()
        .map(Configuration::getVrfs)
        .map(Map::values)
        .flatMap(Collection::stream)
        .flatMap(vrf -> vrf.getInterfaces().values().stream())
        .map(
            iface -> {
              String aclName = iface.getIncomingFilterName();
              String nodeName = iface.getOwner().getName();
              String vrfName = iface.getVrfName();
              String ifaceName = iface.getName();

              BDD inAclBDD =
                  aclName == null ? _bddFactory.one() : _aclPermitBDDs.get(nodeName).get(aclName);
              return new Edge(
                  new PreInInterface(nodeName, ifaceName),
                  new PostInVrf(nodeName, vrfName),
                  inAclBDD);
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreOutEdgePostNat_NodeDropAclOut() {
    return _forwardingAnalysis
        .getArpTrueEdge()
        .keySet()
        .stream()
        .flatMap(
            edge -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              String aclName =
                  _configs.get(node1).getInterfaces().get(iface1).getOutgoingFilterName();
              BDD aclDenyBDD = _aclDenyBDDs.get(node1).get(aclName);

              return aclDenyBDD != null
                  ? Stream.of(
                      new Edge(
                          new PreOutEdgePostNat(node1, iface1, node2, iface2),
                          new NodeDropAclOut(node1),
                          aclDenyBDD))
                  : Stream.of();
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreOutEdgePostNat_PreInInterface() {
    return _forwardingAnalysis
        .getArpTrueEdge()
        .keySet()
        .stream()
        .map(
            edge -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              String aclName =
                  _configs.get(node1).getInterfaces().get(iface1).getOutgoingFilterName();
              BDD aclPermitBDD =
                  aclName == null ? _bddFactory.one() : _aclPermitBDDs.get(node1).get(aclName);

              return new Edge(
                  new PreOutEdgePostNat(node1, iface1, node2, iface2),
                  new PreInInterface(node2, iface2),
                  aclPermitBDD);
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreOutVrf_NodeDropNullRoute() {
    return _forwardingAnalysis
        .getNullRoutedIps()
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry ->
                nodeEntry
                    .getValue()
                    .entrySet()
                    .stream()
                    .map(
                        vrfEntry -> {
                          String node = nodeEntry.getKey();
                          String vrf = vrfEntry.getKey();
                          BDD nullRoutedBDD = vrfEntry.getValue().accept(_ipSpaceToBDD);
                          return new Edge(
                              new PreOutVrf(node, vrf), new NodeDropNullRoute(node), nullRoutedBDD);
                        }));
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreOutVrf_NodeInterfaceNeighborUnreachable() {
    return _neighborUnreachableBDDs
        .entrySet()
        .stream()
        .flatMap(
            nodeEntry -> {
              String node = nodeEntry.getKey();
              return nodeEntry
                  .getValue()
                  .entrySet()
                  .stream()
                  .flatMap(
                      vrfEntry -> {
                        String vrf = vrfEntry.getKey();
                        return vrfEntry
                            .getValue()
                            .entrySet()
                            .stream()
                            .map(
                                ifaceEntry -> {
                                  String iface = ifaceEntry.getKey();
                                  BDD ipSpaceBDD = ifaceEntry.getValue();
                                  String outAcl =
                                      _configs
                                          .get(node)
                                          .getInterfaces()
                                          .get(iface)
                                          .getOutgoingFilterName();
                                  BDD outAclBDD =
                                      outAcl == null
                                          ? _bddFactory.one()
                                          : _aclPermitBDDs.get(node).get(outAcl);
                                  return new Edge(
                                      new PreOutVrf(node, vrf),
                                      new NodeInterfaceNeighborUnreachable(node, iface),
                                      ipSpaceBDD.and(outAclBDD));
                                });
                      });
            });
  }

  @VisibleForTesting
  Stream<Edge> generateRules_PreOutVrf_PreOutEdgePostNat() {
    return _arpTrueEdgeBDDs
        .entrySet()
        .stream()
        .map(
            entry -> {
              org.batfish.datamodel.Edge edge = entry.getKey();
              BDD arpTrue = entry.getValue();

              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String vrf1 = ifaceVrf(edge.getNode1(), edge.getInt1());
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              return new Edge(
                  new PreOutVrf(node1, vrf1),
                  new PreOutEdgePostNat(node1, iface1, node2, iface2),
                  arpTrue);
            });
  }

  @VisibleForTesting
  Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> computeAPTransitions() {
    return toImmutableMap(
        _bddTransitions,
        Entry::getKey,
        preStateEntry ->
            toImmutableMap(
                preStateEntry.getValue(),
                Entry::getKey,
                postStateEntry -> computeAPs(postStateEntry.getValue())));
  }

  public NetworkGraph networkGraph(IpSpaceAssignment assignment) {
    LocationVisitor<StateExpr> toStateExpr =
        new LocationVisitor<StateExpr>() {
          @Override
          public StateExpr visitInterfaceLinkLocation(InterfaceLinkLocation interfaceLinkLocation) {
            return new OriginateInterfaceLink(
                interfaceLinkLocation.getNodeName(), interfaceLinkLocation.getInterfaceName());
          }

          @Override
          public StateExpr visitInterfaceLocation(InterfaceLocation interfaceLocation) {
            String vrf =
                _configs
                    .get(interfaceLocation.getNodeName())
                    .getInterfaces()
                    .get(interfaceLocation.getInterfaceName())
                    .getVrf()
                    .getName();
            return new OriginateVrf(interfaceLocation.getNodeName(), vrf);
          }
        };

    Map<StateExpr, SortedSet<Integer>> roots = new HashMap<>();
    for (IpSpaceAssignment.Entry entry : assignment.getEntries()) {
      SortedSet<Integer> ipSpaceAPs = computeAPs(entry.getIpSpace().accept(_ipSpaceToBDD));
      for (Location loc : entry.getLocations()) {
        StateExpr root = loc.accept(toStateExpr);
        roots.computeIfAbsent(root, k -> new TreeSet<>()).addAll(ipSpaceAPs);
      }
    }

    return new NetworkGraph(roots, _apTransitions);
  }

  @VisibleForTesting
  SortedSet<Integer> computeAPs(IpSpace ipSpace) {
    return computeAPs(ipSpace.accept(_ipSpaceToBDD));
  }

  @VisibleForTesting
  SortedSet<Integer> computeAPs(BDD pred) {
    ImmutableSortedSet.Builder<Integer> apsBuilder =
        new ImmutableSortedSet.Builder<>(Comparator.naturalOrder());
    for (int i = 0; i < _apBDDs.size(); i++) {
      if (!pred.and(_apBDDs.get(i)).isZero()) {
        // there is a non-empty intersection
        apsBuilder.add(i);
      }
    }
    SortedSet<Integer> aps = apsBuilder.build();
    return aps;
  }

  private String ifaceVrf(String node, String iface) {
    return _configs.get(node).getInterfaces().get(iface).getVrfName();
  }

  private static Map<String, Map<String, BDD>> computeVrfAcceptBDDs(
      Map<String, Configuration> configs, IpSpaceToBDD ipSpaceToBDD) {
    Map<String, Map<String, IpSpace>> vrfOwnedIpSpaces =
        CommonUtil.computeVrfOwnedIpSpaces(
            CommonUtil.computeIpVrfOwners(false, CommonUtil.computeNodeInterfaces(configs)));

    return CommonUtil.toImmutableMap(
        vrfOwnedIpSpaces,
        Entry::getKey,
        nodeEntry ->
            CommonUtil.toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry -> vrfEntry.getValue().accept(ipSpaceToBDD)));
  }
}

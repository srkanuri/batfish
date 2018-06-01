package org.batfish.atomicpredicates;

import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpAccessList;
import org.batfish.main.BDDUtils;
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
import org.batfish.z3.state.NodeInterfaceNeighborUnreachable;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreInInterface;
import org.batfish.z3.state.PreOutEdgePostNat;
import org.batfish.z3.state.PreOutVrf;

public class ForwardingAnalysisNetworkGraphFactory {
  private final Map<String, Map<String, BDD>> _aclBDDs;
  private final List<BDD> _apBDDs;
  private final Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> _apTransitions;
  private final Map<StateExpr, Map<StateExpr, BDD>> _bddTransitions;
  private final BDDOps _bddOps;
  private final BDDUtils _bddUtils;
  private final Map<String, Configuration> _configs;
  private final ForwardingAnalysis _forwardingAnalysis;
  private final IpSpaceToBDD _ipSpaceToBDD;

  public ForwardingAnalysisNetworkGraphFactory(
      Map<String, Configuration> configs, ForwardingAnalysis forwardingAnalysis) {
    _bddUtils = new BDDUtils(configs, forwardingAnalysis);
    _bddOps = new BDDOps(_bddUtils.getBDDFactory());
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _ipSpaceToBDD = _bddUtils.getIpSpaceToBDD();
    _aclBDDs = computeAclBDDs();
    _bddTransitions = computeBDDTransitions();
    _apBDDs = computeAPBDDs();
    _apTransitions = computeAPTransitions();
  }

  private Map<String, Map<String, BDD>> computeAclBDDs() {
    return toImmutableMap(
        _configs,
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue().getIpAccessLists(),
                Entry::getKey,
                aclEntry -> {
                  BDDAcl bddAcl = BDDAcl.create(aclEntry.getValue());
                  BDDPacket pkt = bddAcl.getPkt();
                  BDD nonIpVars =
                      _bddOps.and(
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
                          _bddOps.and(pkt.getSrcPort().getBitvec()));
                  return bddAcl.getBdd().exist(nonIpVars);
                }));
  }

  private List<BDD> computeAPBDDs() {
    AtomicPredicates atomicPredicates = new AtomicPredicates(_bddUtils.getBDDFactory());
    return ImmutableList.copyOf(
        atomicPredicates.atomize(
            _bddTransitions
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList())));
  }

  /**
   * private Map<String, Map<String, BDD>> computeBDDTransitionsFib() { Map<String, Map<String,
   * Set<Ip>>> interfaceOwnedIps = null; Map<String, Map<String, Fib>> fibs = null;
   *
   * <p>}
   */

  /**
   * Compute all the BDD transitions for a Vrf in a single-pass, sharing as much of the BDD
   * computation as possible.
   */
  /*
  private void computeVrfBDDTransitions(Set<Ip> ownedIps, Fib fib) {
    BDD ownedIpsBDD = null;

    fib.getNextHopInterfaces()

    List<AbstractRoute> routes = null;
    // sort by increasing prefix length
    routes.sort(
        (route1, route2) -> {
          int byLen = route1.getNetwork().getPrefixLength() - route2.getNetwork().getPrefixLength();
          return byLen != 0
              ? byLen
              : route1.getNetwork().getStartIp().compareTo(route2.getNetwork().getStartIp());
        });
    // sort by decreasing prefix length
    Lists.reverse(routes);

    BDD reach = ownedIpsBDD.not();
    for(AbstractRoute route : routes) {
      BDD forward = reach.and(_ipSpaceToBDD.toBDD(route.getNetwork()));

      fib.getNextHopInterfaces().get(route);
    }
  }
  */

  private Map<StateExpr, Map<StateExpr, BDD>> computeBDDTransitions() {
    Map<StateExpr, Map<StateExpr, BDD>> bddTransitions = new HashMap<>();

    BDD one = _bddOps.getBDDFactory().one();

    /*
     * PreInInterface --> PostInVrf
     * PreInInterface --> Drop
     */
    _configs
        .values()
        .stream()
        .map(Configuration::getVrfs)
        .map(Map::values)
        .flatMap(Collection::stream)
        .forEach(
            vrf ->
                vrf.getInterfaces()
                    .values()
                    .forEach(
                        iface -> {
                          String nodeName = iface.getOwner().getName();
                          String vrfName = vrf.getName();
                          String ifaceName = iface.getName();
                          BDD inAclBDD = _aclBDDs.get(nodeName).getOrDefault(ifaceName, one);
                          bddTransitions.put(
                              new PreInInterface(nodeName, ifaceName),
                              ImmutableMap.of(
                                  new PostInVrf(nodeName, vrfName),
                                  inAclBDD,
                                  Drop.INSTANCE,
                                  inAclBDD.not()));
                        }));

    // PreOutVrf --> NeighborUnreachable
    _forwardingAnalysis
        .getNeighborUnreachable()
        .forEach(
            (node, vrfIpSpaces) ->
                vrfIpSpaces.forEach(
                    (vrf, ifaceIpSpaces) ->
                        ifaceIpSpaces.forEach(
                            (iface, ipSpace) -> {
                              BDD ipSpaceBDD = ipSpace.accept(_ipSpaceToBDD);
                              IpAccessList outAcl =
                                  _configs.get(node).getInterfaces().get(iface).getOutgoingFilter();
                              BDD outAclBDD =
                                  outAcl == null ? one : _aclBDDs.get(node).get(outAcl.getName());
                              bddTransitions
                                  .computeIfAbsent(new PreOutVrf(node, vrf), k -> new HashMap<>())
                                  .put(
                                      new NodeInterfaceNeighborUnreachable(node, iface),
                                      ipSpaceBDD.and(outAclBDD));

                              // add the single out-edge for this state
                              bddTransitions.put(
                                  new NodeInterfaceNeighborUnreachable(node, iface),
                                  ImmutableMap.of(NeighborUnreachable.INSTANCE, one));
                            })));

    _forwardingAnalysis
        .getNullRoutedIps()
        .forEach(
            (node, vrfIpSpaces) ->
                vrfIpSpaces.forEach(
                    (vrf, ipSpace) -> {
                      bddTransitions
                          .computeIfAbsent(new PreOutVrf(node, vrf), k -> new HashMap<>())
                          .put(Drop.INSTANCE, ipSpace.accept(_ipSpaceToBDD));
                    }));

    _forwardingAnalysis
        .getArpTrueEdge()
        .forEach(
            (edge, arpTrue) -> {
              String node1 = edge.getNode1();
              String iface1 = edge.getInt1();
              String vrf1 = ifaceVrf(edge.getNode1(), edge.getInt1());
              String node2 = edge.getNode2();
              String iface2 = edge.getInt2();

              BDD bdd = arpTrue.accept(_ipSpaceToBDD);

              bddTransitions
                  .computeIfAbsent(new PreOutVrf(node1, vrf1), k -> new HashMap<>())
                  // not modelling NAT, so skip it.
                  .put(new PreOutEdgePostNat(node1, iface1, node2, iface2), bdd);

              BDD outAcl = _aclBDDs.get(node1).getOrDefault(iface1, one);
              bddTransitions.put(
                  new PreOutEdgePostNat(node1, iface1, node2, iface2),
                  ImmutableMap.of(
                      new PreInInterface(node2, iface2), outAcl, Drop.INSTANCE, outAcl.not()));
            });

    _bddUtils
        .computeVrfAcceptBDDs()
        .forEach(
            (node, vrfAcceptBDDs) ->
                vrfAcceptBDDs.forEach(
                    (vrf, acceptBDD) -> {
                      Map<StateExpr, BDD> vrfTransitions =
                          bddTransitions.computeIfAbsent(
                              new PostInVrf(node, vrf), k -> new HashMap<>());
                      BDD routableBDD =
                          _forwardingAnalysis
                              .getRoutableIps()
                              .get(node)
                              .get(vrf)
                              .accept(_ipSpaceToBDD);
                      BDD forwardBDD = acceptBDD.not();
                      vrfTransitions.put(new NodeAccept(node), acceptBDD);
                      vrfTransitions.put(new PreOutVrf(node, vrf), forwardBDD.and(routableBDD));
                      vrfTransitions.put(Drop.INSTANCE, forwardBDD.and(routableBDD.not()));
                    }));

    _configs
        .keySet()
        .forEach(
            node ->
                bddTransitions.put(new NodeAccept(node), ImmutableMap.of(Accept.INSTANCE, one)));
    return bddTransitions;
  }

  private Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> computeAPTransitions() {
    return toImmutableMap(
        _bddTransitions,
        Entry::getKey,
        preStateEntry ->
            toImmutableMap(
                preStateEntry.getValue(),
                Entry::getKey,
                postStateEntry -> {
                  BDD precondition = postStateEntry.getValue();

                  ImmutableSortedSet.Builder<Integer> apPreconditions =
                      new ImmutableSortedSet.Builder<>(Comparator.naturalOrder());
                  int index = 0;
                  for (BDD bdd : _apBDDs) {
                    if (!bdd.and(precondition).isZero()) {
                      apPreconditions.add(index);
                      index++;
                    }
                  }
                  return apPreconditions.build();
                }));
  }

  public NetworkGraph networkGraph() {
    // originate any traffic from every VRF
    Set<StateExpr> roots =
        _configs
            .values()
            .stream()
            .flatMap(
                config ->
                    config
                        .getVrfs()
                        .values()
                        .stream()
                        .map(v -> new PostInVrf(config.getHostname(), v.getName())))
            .collect(ImmutableSet.toImmutableSet());

    return new NetworkGraph(_apBDDs, roots, _apTransitions);
  }

  private String ifaceVrf(String node, String iface) {
    return _configs.get(node).getInterfaces().get(iface).getVrfName();
  }
}

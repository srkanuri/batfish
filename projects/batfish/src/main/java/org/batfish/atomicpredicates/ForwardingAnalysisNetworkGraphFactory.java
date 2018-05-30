package org.batfish.atomicpredicates;

import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
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
import org.batfish.main.BDDUtils;
import org.batfish.symbolic.bdd.AtomicPredicates;
import org.batfish.symbolic.bdd.IpSpaceToBDD;

public class ForwardingAnalysisNetworkGraphFactory {
  private final List<BDD> _apBDDs;
  private final Map<String, Map<String, SortedSet<Integer>>> _apTransitions;
  private final Map<String, Map<String, BDD>> _bddTransitions;
  private final BDDUtils _bddUtils;
  private final Map<String, Configuration> _configs;
  private final ForwardingAnalysis _forwardingAnalysis;
  private final IpSpaceToBDD _ipSpaceToBDD;

  public ForwardingAnalysisNetworkGraphFactory(
      Map<String, Configuration> configs, ForwardingAnalysis forwardingAnalysis) {
    _bddUtils = new BDDUtils(configs, forwardingAnalysis);
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _ipSpaceToBDD = _bddUtils.getIpSpaceToBDD();
    _bddTransitions = computeBDDTransitions();
    _apBDDs = computeAPBDDs();
    _apTransitions = computeAPTransitions(_bddTransitions);
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

  private Map<String, Map<String, BDD>> computeBDDTransitions() {
    Map<String, Map<String, BDD>> bddTransitions = new HashMap<>();
    _forwardingAnalysis
        .getNeighborUnreachable()
        .forEach(
            (node, vrfIpSpaces) ->
                vrfIpSpaces.forEach(
                    (vrf, ifaceIpSpaces) ->
                        ifaceIpSpaces.forEach(
                            (iface, ipSpace) ->
                                bddTransitions
                                    .computeIfAbsent(vrfForward(node, vrf), k -> new HashMap<>())
                                    .put(
                                        neighborUnreachable(node, vrf, iface),
                                        ipSpace.accept(_ipSpaceToBDD)))));

    _forwardingAnalysis
        .getNullRoutedIps()
        .forEach(
            (node, vrfIpSpaces) ->
                vrfIpSpaces.forEach(
                    (vrf, ipSpace) ->
                        bddTransitions
                            .computeIfAbsent(vrfForward(node, vrf), k -> new HashMap<>())
                            .put(vrfNullRouted(node, vrf), ipSpace.accept(_ipSpaceToBDD))));

    _forwardingAnalysis
        .getArpTrueEdge()
        .forEach(
            (edge, arpTrue) -> {
              String node1 = edge.getNode1();
              String vrf1 = ifaceVrf(edge.getNode1(), edge.getInt1());
              String node2 = edge.getNode2();
              String vrf2 = ifaceVrf(edge.getNode2(), edge.getInt2());

              BDD bdd = arpTrue.accept(_ipSpaceToBDD);

              bddTransitions
                  .computeIfAbsent(vrfForward(node1, vrf1), k -> new HashMap<>())
                  .merge(vrf(node2, vrf2), bdd, BDD::or);
            });

    _bddUtils
        .computeVrfAcceptBDDs()
        .forEach(
            (node, vrfAcceptBDDs) ->
                vrfAcceptBDDs.forEach(
                    (vrf, acceptBDD) -> {
                      Map<String, BDD> vrfTransitions =
                          bddTransitions.computeIfAbsent(vrf(node, vrf), k -> new HashMap<>());
                      BDD routableBDD =
                          _forwardingAnalysis
                              .getRoutableIps()
                              .get(node)
                              .get(vrf)
                              .accept(_ipSpaceToBDD);
                      BDD forwardBDD = acceptBDD.not();
                      vrfTransitions.put(vrfAccept(node, vrf), acceptBDD);
                      vrfTransitions.put(vrfForward(node, vrf), forwardBDD.and(routableBDD));
                      vrfTransitions.put(vrfDrop(node, vrf), forwardBDD.and(routableBDD.not()));
                    }));

    return bddTransitions;
  }

  private Map<String, Map<String, SortedSet<Integer>>> computeAPTransitions(
      Map<String, Map<String, BDD>> bddTransitions) {
    return toImmutableMap(
        bddTransitions,
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
    Set<String> roots =
        _configs
            .values()
            .stream()
            .flatMap(
                config ->
                    config
                        .getVrfs()
                        .values()
                        .stream()
                        .map(v -> vrf(config.getHostname(), v.getName())))
            .collect(ImmutableSet.toImmutableSet());

    return new NetworkGraph(_apBDDs, roots, _apTransitions);
  }

  private String ifaceVrf(String node, String iface) {
    return _configs.get(node).getInterfaces().get(iface).getVrfName();
  }

  private static String vrf(String node, String vrf) {
    return String.format("Vrf %s %s", node, vrf);
  }

  private static String vrfAccept(String node, String vrf) {
    return String.format("VrfAccept %s %s", node, vrf);
  }

  private static String vrfDrop(String node, String vrf) {
    return String.format("VrfDrop %s %s", node, vrf);
  }

  private static String vrfForward(String node, String vrf) {
    return String.format("VrfForward %s %s", node, vrf);
  }

  private static String neighborUnreachable(String node, String vrf, String iface) {
    return String.format("NeighborUnreachable %s %s %s", node, vrf, iface);
  }

  private static String vrfNullRouted(String node, String vrf) {
    return String.format("VrfNullRouted %s %s", node, vrf);
  }
}

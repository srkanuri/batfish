package org.batfish.atomicpredicates;

import static org.batfish.common.util.CommonUtil.toImmutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDException;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.JFactory;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpSpace;
import org.batfish.main.BDDUtils;
import org.batfish.symbolic.bdd.AtomicPredicates;
import org.batfish.symbolic.bdd.BDDAcl;
import org.batfish.symbolic.bdd.BDDInteger;
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
  private ParallelIpSpaceToBDD _parallelIpSpaceToBDD;

  public ForwardingAnalysisNetworkGraphFactory(
      Map<String, Configuration> configs, ForwardingAnalysis forwardingAnalysis) {
    _bddUtils = new BDDUtils(configs, forwardingAnalysis);
    _bddOps = new BDDOps(_bddUtils.getBDDFactory());
    _configs = configs;
    _forwardingAnalysis = forwardingAnalysis;
    _ipSpaceToBDD = _bddUtils.getIpSpaceToBDD();

    benchmark();

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(_bddOps.getBDDFactory(), 2);
    _aclBDDs = computeAclBDDs();
    _bddTransitions = computeBDDTransitions();
    _apBDDs = computeAPBDDs();
    _apTransitions = computeAPTransitions();
  }

  private class ParallelIpSpaceToBDD {
    private final BlockingQueue<BDDFactory> _generatorFactories;
    private final BDDFactory _loaderFactory;
    private BDD _loaderFactoryOne;
    private BDD _loaderFactoryZero;

    ParallelIpSpaceToBDD(BDDFactory loaderFactory, int capacity) {
      _generatorFactories = new ArrayBlockingQueue<>(capacity);
      _loaderFactory = loaderFactory;
      _loaderFactoryOne = loaderFactory.one();
      _loaderFactoryZero = loaderFactory.zero();

      while (_generatorFactories.remainingCapacity() > 0) {
        _generatorFactories.add(newBDDFactory());
      }
    }

    BDD toBDD(IpSpace ipSpace) {
      BDDFactory generatorFactory = null;
      try {
        generatorFactory = _generatorFactories.take();
        BDD result = toBDD(ipSpace, generatorFactory);
        _generatorFactories.put(generatorFactory);
        return result;
      } catch (InterruptedException e) {
        throw new BatfishException("Interrupted", e);
      }
    }

    BDD toBDD(IpSpace ipSpace, BDDFactory generatorFactory) {
      // magic numbers: match the indexes used in BDDPacket (and thus _loaderFactory) for dstIp ;)
      BDDInteger dstIp = BDDInteger.makeFromIndex(generatorFactory, 32, 8, true);
      IpSpaceToBDD ipSpaceToBDD = new IpSpaceToBDD(generatorFactory, dstIp);
      BDD bdd = ipSpace.accept(ipSpaceToBDD);

      // special case: one, zero crash (de)serialization
      if (bdd.isOne()) {
        return _loaderFactoryOne;
      }
      if (bdd.isZero()) {
        return _loaderFactoryZero;
      }

      StringWriter stringWriter = new StringWriter();
      BufferedWriter writer = new BufferedWriter(stringWriter);

      try {
        generatorFactory.save(writer, bdd);
      } catch (IOException e) {
        throw new BatfishException("Error saving BDD", e);
      }

      try {
        BufferedReader reader = new BufferedReader(new StringReader(stringWriter.toString()));
        synchronized (_loaderFactory) {
          return _loaderFactory.load(reader);
        }
      } catch (IOException | BDDException e) {
        throw new BatfishException("Error loading BDD", e);
      }
    }
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
  private void benchmark() {
    long start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsSequential();
    long sequential = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 1);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel1 = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 2);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel2 = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 4);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel4 = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 8);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel8 = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 16);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel16 = System.currentTimeMillis() - start;

    _parallelIpSpaceToBDD = new ParallelIpSpaceToBDD(newBDDFactory(), 32);
    start = System.currentTimeMillis();
    computeNeighborUnreachableBDDsParallel();
    long parallel32 = System.currentTimeMillis() - start;
  }

  public Map<String, Map<String, Map<String, BDD>>> computeNeighborUnreachableBDDsSequential() {
    return toImmutableMap(
        _forwardingAnalysis.getNeighborUnreachable(),
        Entry::getKey,
        nodeEntry ->
            toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry ->
                    toImmutableMap(
                        vrfEntry.getValue(),
                        Entry::getKey,
                        ifaceEntry -> ifaceEntry.getValue().accept(_ipSpaceToBDD))));
  }

  public Map<String, Map<String, Map<String, BDD>>> computeNeighborUnreachableBDDsParallel() {
    class Tuple<T> {
      String _node;
      String _vrf;
      String _iface;
      T _ipSpace;

      Tuple(String node, String vrf, String iface, T ipSpace) {
        _node = node;
        _vrf = vrf;
        _iface = iface;
        _ipSpace = ipSpace;
      }

      <U> Tuple<U> mapIpSpace(Function<T, U> mapper) {
        return new Tuple<>(_node, _vrf, _iface, mapper.apply(_ipSpace));
      }
    }

    Map<String, Map<String, Map<String, BDD>>> result = new HashMap<>();
    _forwardingAnalysis
        .getNeighborUnreachable()
        .entrySet()
        .parallelStream()
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
                                  IpSpace ipSpace = ifaceEntry.getValue();
                                  return new Tuple<>(node, vrf, iface, ipSpace);
                                });
                      });
            })
        // creating extra work
        .flatMap(t -> Stream.of(t, t, t, t, t))
        .map(t -> t.mapIpSpace(_parallelIpSpaceToBDD::toBDD))
        .forEachOrdered(
            t ->
                result
                    .computeIfAbsent(t._node, k -> new HashMap<>())
                    .computeIfAbsent(t._vrf, k -> new HashMap<>())
                    .put(t._iface, t._ipSpace));
    return result;
  }

  private static BDDFactory newBDDFactory() {
    BDDFactory factory = JFactory.init(10000, 1000);
    factory.disableReorder();
    factory.setCacheRatio(64);
    factory.setVarNum(40);
    return factory;
  }

  private Map<StateExpr, Map<StateExpr, BDD>> computeBDDTransitions() {
    Map<StateExpr, Map<StateExpr, BDD>> bddTransitions = new HashMap<>();

    BDD one = _bddOps.getBDDFactory().one();

    BlockingQueue<BDDFactory> bddFactories = new ArrayBlockingQueue<>(2);
    bddFactories.add(newBDDFactory());
    bddFactories.add(newBDDFactory());

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
        .flatMap(vrf -> vrf.getInterfaces().values().stream())
        .forEach(
            iface -> {
              String nodeName = iface.getOwner().getName();
              String vrfName = iface.getVrfName();
              String ifaceName = iface.getName();

              BDD inAclBDD = _aclBDDs.get(nodeName).getOrDefault(ifaceName, one);
              bddTransitions.put(
                  new PreInInterface(nodeName, ifaceName),
                  ImmutableMap.of(
                      new PostInVrf(nodeName, vrfName), inAclBDD, Drop.INSTANCE, inAclBDD.not()));
            });

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

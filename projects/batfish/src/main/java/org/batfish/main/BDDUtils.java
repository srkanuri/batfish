package org.batfish.main;

import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.JFactory;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.EmptyIpSpace;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.datamodel.IpSpace;
import org.batfish.datamodel.Vrf;
import org.batfish.symbolic.bdd.AtomicPredicates;
import org.batfish.symbolic.bdd.BDDAcl;
import org.batfish.symbolic.bdd.BDDInteger;
import org.batfish.symbolic.bdd.BDDOps;
import org.batfish.symbolic.bdd.IpSpaceToBDD;

public class BDDUtils {
  private final BDDOps _bddOps;
  private final BDDFactory _factory;
  private final BDDInteger _ipAddrBdd;
  private final IpSpaceToBDD _ipSpaceToBDD;
  private final ForwardingAnalysis _forwardingAnalysis;

  private final Map<String, Configuration> _configs;

  public BDDUtils(
      Map<String, Configuration> configurations, ForwardingAnalysis forwardingAnalysis) {
    _factory = JFactory.init(10000, 1000);
    _factory.disableReorder();
    _factory.setCacheRatio(64);
    _factory.setVarNum(32); // reserve 32 1-bit variables

    _ipAddrBdd = BDDInteger.makeFromIndex(_factory, 32, 0, true);
    _ipSpaceToBDD = new IpSpaceToBDD(_factory, _ipAddrBdd);
    _bddOps = new BDDOps(_factory);
    _forwardingAnalysis = forwardingAnalysis;

    _configs = configurations;
  }

  public IpSpaceToBDD getIpSpaceToBDD() {
    return _ipSpaceToBDD;
  }

  public BDD anyVrfAccepts() {
    return _bddOps.or(
        computeVrfAcceptBDDs()
            .values()
            .stream()
            .flatMap(nodeMap -> nodeMap.values().stream())
            .collect(Collectors.toList()));
  }

  public BDD anyVrfDrops() {
    return _bddOps.or(
        computeVrfDropBDDs()
            .values()
            .stream()
            .flatMap(nodeMap -> nodeMap.values().stream())
            .collect(Collectors.toList()));
  }

  public Map<String, Map<String, BDD>> computeVrfAcceptBDDs() {
    Map<String, Map<String, IpSpace>> vrfOwnedIpSpaces =
        CommonUtil.computeVrfOwnedIpSpaces(
            CommonUtil.computeIpVrfOwners(false, CommonUtil.computeNodeInterfaces(_configs)));

    return CommonUtil.toImmutableMap(
        vrfOwnedIpSpaces,
        Entry::getKey,
        nodeEntry ->
            CommonUtil.toImmutableMap(
                nodeEntry.getValue(),
                Entry::getKey,
                vrfEntry -> vrfEntry.getValue().accept(new IpSpaceToBDD(_factory, _ipAddrBdd))));
  }

  public Map<String, Map<String, BDD>> computeVrfDropBDDs() {
    return CommonUtil.toImmutableMap(
        _configs,
        Entry::getKey,
        nodeEntry ->
            CommonUtil.toImmutableMap(
                nodeEntry.getValue().getVrfs(),
                Entry::getKey,
                vrfEntry -> computeVrfDropBDDs(nodeEntry.getKey(), vrfEntry.getValue())));
  }

  private BDD computeVrfDropBDDs(String node, Vrf vrf) {
    BDD passIncomingFilter =
        vrf.getInterfaces()
            .values()
            .stream()
            .map(iface -> BDDAcl.create(iface.getIncomingFilter()).getBdd())
            .reduce(_factory.zero(), BDD::or);

    BDD routable =
        _forwardingAnalysis.getRoutableIps().get(node).get(vrf.getName()).accept(_ipSpaceToBDD);

    BDD failsOutgoingFilter =
        vrf.getInterfaces()
            .values()
            .stream()
            .map(
                iface -> {
                  BDD arpTrue =
                      _forwardingAnalysis
                          .getArpReplies()
                          .get(node)
                          .getOrDefault(iface.getName(), EmptyIpSpace.INSTANCE)
                          .accept(_ipSpaceToBDD);
                  BDD aclOut = BDDAcl.create(iface.getOutgoingFilter()).getBdd();
                  return arpTrue.and(aclOut.not());
                })
            .reduce(_factory.zero(), BDD::or);

    BDD nullRouted =
        _forwardingAnalysis.getNullRoutedIps().get(node).get(vrf.getName()).accept(_ipSpaceToBDD);

    BDD unroutable = routable.not();

    return _bddOps.or(
        passIncomingFilter.not(),
        passIncomingFilter.and(
            _bddOps.or(unroutable, routable.and(_bddOps.or(nullRouted, failsOutgoingFilter)))));
  }

  public static Set<BDD> computeAtomicPredicates(
      ForwardingAnalysis forwardingAnalysis, Map<String, Configuration> configurations) {

    BDDFactory factory = JFactory.init(10000, 1000);
    factory.disableReorder();
    factory.setCacheRatio(64);
    factory.setVarNum(32); // reserve 32 1-bit variables
    BDDInteger ipAddrBdd = BDDInteger.makeFromIndex(factory, 32, 0, true);
    IpSpaceToBDD ipSpaceToBdd = new IpSpaceToBDD(factory, ipAddrBdd);
    AtomicPredicates atomicPredicates = new AtomicPredicates(factory);

    long timeInitialPredicates = System.currentTimeMillis();
    Set<BDD> initialPredicates =
        Streams.concat(
                forwardingAnalysis.getArpTrueEdge().values().stream(),
                forwardingAnalysis
                    .getNeighborUnreachable()
                    .values()
                    .stream()
                    .flatMap(map -> map.values().stream().flatMap(map2 -> map2.values().stream())),
                forwardingAnalysis
                    .getNullRoutedIps()
                    .values()
                    .stream()
                    .flatMap(map -> map.values().stream()),
                forwardingAnalysis
                    .getRoutableIps()
                    .values()
                    .stream()
                    .flatMap(map -> map.values().stream()))
            .map(ipSpaceToBdd::visit)
            .collect(Collectors.toSet());
    timeInitialPredicates = System.currentTimeMillis() - timeInitialPredicates;

    return atomicPredicates.atomize(initialPredicates);
  }

  public BDD acceptedAndDropped() {
    List<BDD> accepts =
        computeVrfAcceptBDDs()
            .values()
            .stream()
            .map(Map::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    List<BDD> drops =
        computeVrfDropBDDs()
            .values()
            .stream()
            .map(Map::values)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    BDD r = _factory.zero();
    for (BDD accept : accepts) {
      for (BDD drop : drops) {
        r = r.orWith(accept.and(drop));
      }
    }
    return r;
  }

  public BDDFactory getBDDFactory() {
    return _bddOps.getBDDFactory();
  }
}

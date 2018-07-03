package org.batfish.atomicpredicates;

import static org.batfish.datamodel.Configuration.DEFAULT_VRF_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.AclIpSpace;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.IpSpace;
import org.batfish.specifier.InterfaceLocation;
import org.batfish.specifier.IpSpaceAssignment;
import org.batfish.symbolic.bdd.BDDOps;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.Accept;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeDropNoRoute;
import org.batfish.z3.state.OriginateVrf;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreInInterface;
import org.batfish.z3.state.PreOutEdgePostNat;
import org.batfish.z3.state.PreOutVrf;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

public class NetworkGraphTest {
  private TwoNodeNetworkWithTwoLinks _net;
  private NetworkGraphFactory _graphFactory;

  @Before
  public void setup() throws IOException {
    _net = new TwoNodeNetworkWithTwoLinks();
    _net._batfish.computeDataPlane(false);
    DataPlane dataPlane = _net._batfish.loadDataPlane();
    _graphFactory =
        new NetworkGraphFactory(
            _net._configs, dataPlane.getForwardingAnalysis(), BDDTrieAtomizer::new, true);
  }

  @Test
  public void testAPsDisjoint() {
    for (BDD ap1 : _graphFactory.getApBDDs()) {
      for (BDD ap2 : _graphFactory.getApBDDs()) {
        assert (ap1 == ap2 || ap1.and(ap2).isZero());
      }
    }
  }

  @Test
  public void testAPsCoverBDDs() {
    BDDOps ops = new BDDOps(_graphFactory.getBDDFactory());
    List<BDD> apBDDs = _graphFactory.getApBDDs();
    Map<StateExpr, Map<StateExpr, BDD>> bddTransitions = _graphFactory.getBDDTransitions();
    Map<StateExpr, Map<StateExpr, SortedSet<Integer>>> apTransitions =
        _graphFactory.computeAPTransitions();
    apTransitions.forEach(
        (src, srcTransitions) ->
            srcTransitions.forEach(
                (dst, aps) -> {
                  BDD union = ops.or(aps.stream().map(apBDDs::get).collect(Collectors.toList()));
                  BDD orig = bddTransitions.get(src).get(dst);
                  assertThat(String.format("%s -> %s", src, dst), union.biimp(orig).isOne());
                }));
  }

  @Test
  public void testBDDTransitions() {
    String dstIface1 = _net._link1Dst.getName();
    String dstIface2 = _net._link2Dst.getName();
    String dstNode = _net._dstNode.getHostname();
    String srcIface1 = _net._link1Src.getName();
    String srcIface2 = _net._link2Src.getName();
    String srcNode = _net._srcNode.getHostname();

    IpSpace srcIface1IpSpace = _net._link1Src.getAddress().getIp().toIpSpace();
    IpSpace srcIface2IpSpace = _net._link2Src.getAddress().getIp().toIpSpace();
    IpSpace unionIpSpace = AclIpSpace.union(srcIface1IpSpace, srcIface2IpSpace);
    BDD expectedAcceptBDD = unionIpSpace.accept(_graphFactory.getIpSpaceToBDD());
    BDD actualAcceptBDD = _graphFactory.getVrfAcceptBDDs().get(srcNode).get(DEFAULT_VRF_NAME);
    assertThat("expected = actual", expectedAcceptBDD.biimp(actualAcceptBDD).isOne());

    BDD dstIface1BDD =
        _net._link1Dst.getAddress().getIp().toIpSpace().accept(_graphFactory.getIpSpaceToBDD());
    assertThat("", dstIface1BDD.and(actualAcceptBDD).isZero());

    BDD dstIface2BDD =
        _net._link1Dst.getAddress().getIp().toIpSpace().accept(_graphFactory.getIpSpaceToBDD());
    assertThat("", dstIface2BDD.and(actualAcceptBDD).isZero());
  }

  @Test
  public void test1() {
    String dstIface1 = _net._link1Dst.getName();
    String dstIface2 = _net._link2Dst.getName();
    String dstNode = _net._dstNode.getHostname();
    String srcIface1 = _net._link1Src.getName();
    String srcIface2 = _net._link2Src.getName();
    String srcNode = _net._srcNode.getHostname();

    IpSpace dstIface1IpSpace = _net._link1Dst.getAddress().getIp().toIpSpace();
    IpSpace dstIface2IpSpace = _net._link2Dst.getAddress().getIp().toIpSpace();
    IpSpace unionIpSpace = AclIpSpace.union(dstIface1IpSpace, dstIface2IpSpace);
    SortedSet<Integer> dstIface1APs = _graphFactory.computeAPs(dstIface1IpSpace);
    SortedSet<Integer> dstIface2APs = _graphFactory.computeAPs(dstIface2IpSpace);
    SortedSet<Integer> unionAPs = _graphFactory.computeAPs(unionIpSpace);

    assertThat(unionAPs, equalTo(Sets.union(dstIface1APs, dstIface2APs)));

    // TODO NetworkGraph's constructor should take the IpSpaceAssignment.
    // Those IpSpaces need to be part of the AP computation.
    NetworkGraph graph =
        _graphFactory.networkGraph(
            IpSpaceAssignment.builder()
                .assign(new InterfaceLocation(srcNode, srcIface1), unionIpSpace)
                .build());

    Map<StateExpr, Multimap<Integer, StateExpr>> result = graph.getReachableAps();
    OriginateVrf originateVrf = new OriginateVrf(srcNode, DEFAULT_VRF_NAME);
    PostInVrf srcPostInVrf = new PostInVrf(srcNode, DEFAULT_VRF_NAME);
    PreOutVrf preOutVrf = new PreOutVrf(srcNode, DEFAULT_VRF_NAME);
    PreOutEdgePostNat preOutEdgePostNat1 =
        new PreOutEdgePostNat(srcNode, srcIface1, dstNode, dstIface1);
    PreInInterface preInInterface1 = new PreInInterface(dstNode, dstIface1);

    PreOutEdgePostNat preOutEdgePostNat2 =
        new PreOutEdgePostNat(srcNode, srcIface2, dstNode, dstIface2);
    PreInInterface preInInterface2 = new PreInInterface(dstNode, dstIface2);

    PostInVrf dstPostInVrf = new PostInVrf(dstNode, DEFAULT_VRF_NAME);
    NodeAccept dstNodeAccept = new NodeAccept(dstNode);

    // common first path segment
    assertReaches(result, unionAPs, originateVrf, originateVrf, srcPostInVrf, preOutVrf);

    // separate path segments per destination
    assertReaches(result, dstIface1APs, originateVrf, preOutEdgePostNat1, preInInterface1);
    assertReaches(result, dstIface2APs, originateVrf, preOutEdgePostNat2, preInInterface2);

    // common end path segment
    assertReaches(result, unionAPs, originateVrf, dstPostInVrf, dstNodeAccept, Accept.INSTANCE);

    assertNoReachingAPs(result, new NodeDropNoRoute(srcNode), new NodeAccept(srcNode));
  }

  private void assertNoReachingAPs(
      Map<StateExpr, Multimap<Integer, StateExpr>> result, StateExpr... stateExprs) {
    for (StateExpr stateExpr : stateExprs) {
      assertReachingAPs(result, stateExpr, equalTo(ImmutableSortedSet.of()));
    }
  }

  private void assertReachingAPs(
      Map<StateExpr, Multimap<Integer, StateExpr>> reaches,
      StateExpr dst,
      Matcher<? super Set<Integer>> apsMatcher) {
    assertThat(reaches, hasKey(dst));
    assertThat(reaches.get(dst).asMap().keySet(), apsMatcher);
  }

  private void assertReaches(
      Map<StateExpr, Multimap<Integer, StateExpr>> reaches,
      SortedSet<Integer> aps,
      StateExpr src,
      StateExpr... dsts) {
    for (StateExpr dst : dsts) {
      for (Integer ap : aps) {
        assertThat(String.format("reaches missing dst %s", dst), reaches, hasKey(dst));
        assertThat(
            String.format("AP %s should reach %s", ap, dst), reaches.get(dst).asMap(), hasKey(ap));
        assertThat(
            String.format("AP %s should reach %s from %s", ap, dst, src),
            reaches.get(dst).asMap(),
            hasEntry(equalTo(ap), contains(src)));
      }
    }
  }
}

package org.batfish.atomicpredicates;

import static org.batfish.atomicpredicates.TwoNodeNetworkWithTwoLinks.DST_PREFIX_1;
import static org.batfish.atomicpredicates.TwoNodeNetworkWithTwoLinks.DST_PREFIX_2;
import static org.batfish.datamodel.Configuration.DEFAULT_VRF_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.atomicpredicates.NetworkGraph.MultipathConsistencyViolation;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.UniverseIpSpace;
import org.batfish.specifier.InterfaceLocation;
import org.batfish.specifier.IpSpaceAssignment;
import org.batfish.symbolic.bdd.BDDInteger;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.Accept;
import org.batfish.z3.state.Drop;
import org.batfish.z3.state.NeighborUnreachable;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeDropAclIn;
import org.batfish.z3.state.NodeDropNoRoute;
import org.batfish.z3.state.NodeDropNullRoute;
import org.batfish.z3.state.NodeInterfaceNeighborUnreachable;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreInInterface;
import org.batfish.z3.state.PreOutEdgePostNat;
import org.batfish.z3.state.PreOutVrf;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ForwardingAnalysisNetworkGraphFactoryTest {
  private static NetworkGraph GRAPH;
  private static ForwardingAnalysisNetworkGraphFactory GRAPH_FACTORY;
  private static TwoNodeNetworkWithTwoLinks NET;

  private Ip _dstIface1Ip;
  private Ip _dstIface2Ip;
  private String _dstIface1Name;
  private String _dstIface2Name;
  private String _dstName;
  private PostInVrf _dstPostInVrf;
  private PreOutVrf _dstPreOutVrf;

  private String _link1DstName;
  private String _link2DstName;

  private String _srcName;
  private PostInVrf _srcPostInVrf;

  @BeforeClass
  public static void initGraphFactory() throws IOException {
    NET = new TwoNodeNetworkWithTwoLinks();
    NET._batfish.computeDataPlane(false);
    DataPlane dataPlane = NET._batfish.loadDataPlane();
    GRAPH_FACTORY =
        new ForwardingAnalysisNetworkGraphFactory(NET._configs, dataPlane.getForwardingAnalysis());

    IpSpaceAssignment assignment =
        IpSpaceAssignment.builder()
            .assign(
                new InterfaceLocation(NET._srcNode.getName(), NET._link1Src.getName()),
                UniverseIpSpace.INSTANCE)
            .build();
    GRAPH = GRAPH_FACTORY.networkGraph(assignment);
  }

  @Before
  public void setup() {
    _dstIface1Ip = DST_PREFIX_1.getStartIp();
    _dstIface2Ip = DST_PREFIX_2.getStartIp();
    _dstIface1Name = NET._dstIface1.getName();
    _dstIface2Name = NET._dstIface2.getName();
    _dstName = NET._dstNode.getHostname();
    _dstPostInVrf = new PostInVrf(_dstName, DEFAULT_VRF_NAME);
    _dstPreOutVrf = new PreOutVrf(_dstName, DEFAULT_VRF_NAME);

    _link1DstName = NET._link1Dst.getName();
    _link2DstName = NET._link2Dst.getName();

    _srcName = NET._srcNode.getHostname();
    _srcPostInVrf = new PostInVrf(_srcName, DEFAULT_VRF_NAME);
  }

  private List<Ip> bddIps(BDD bdd) {
    BDDInteger bddInteger = GRAPH_FACTORY.getIpSpaceToBDD().getBDDInteger();

    return bddInteger
        .getValuesSatisfying(bdd, 10)
        .stream()
        .map(Ip::new)
        .collect(Collectors.toList());
  }

  private static BDD bddTransition(StateExpr preState, StateExpr postState) {
    return GRAPH_FACTORY.getBDDTransitions().get(preState).get(postState);
  }

  private static BDD ipBDD(Ip ip) {
    return GRAPH_FACTORY.getIpSpaceToBDD().toBDD(ip);
  }

  private static BDD vrfAcceptBDD(String node) {
    return GRAPH_FACTORY.getVrfAcceptBDDs().get(node).get(DEFAULT_VRF_NAME);
  }

  @Test
  public void testVrfAcceptBDDs_dst() {
    assertThat(
        bddIps(vrfAcceptBDD(_dstName)),
        containsInAnyOrder(new Ip("1.0.0.1"), new Ip("2.0.0.1"), new Ip("2.1.0.0"), _dstIface1Ip));
  }

  @Test
  public void testVrfAcceptBDDs_src() {
    assertThat(
        bddIps(vrfAcceptBDD(_srcName)), containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_outEdges() {
    BDD nodeAccept = bddTransition(_srcPostInVrf, new NodeAccept(_srcName));
    BDD nodeDropNoRoute = bddTransition(_srcPostInVrf, new NodeDropNoRoute(_srcName));
    BDD preOutVrf = bddTransition(_srcPostInVrf, new PreOutVrf(_srcName, DEFAULT_VRF_NAME));

    // test that out edges are mutually exclusive
    assertThat(nodeAccept.and(nodeDropNoRoute).isZero(), is(true));
    assertThat(nodeAccept.and(preOutVrf).isZero(), is(true));
    assertThat(nodeDropNoRoute.and(preOutVrf).isZero(), is(true));
  }

  @Test
  public void testBDDTransitions_PostInVrf_NodeAccept() {
    assertThat(
        bddIps(bddTransition(_srcPostInVrf, new NodeAccept(_srcName))),
        containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_PreOutVrf_dst() {
    assertThat(
        bddIps(bddTransition(_dstPostInVrf, _dstPreOutVrf)),
        containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_PreOutVrf_src() {
    PostInVrf preState = new PostInVrf(_srcName, DEFAULT_VRF_NAME);
    PreOutVrf postState = new PreOutVrf(_srcName, DEFAULT_VRF_NAME);
    assertThat(
        bddIps(bddTransition(preState, postState)),
        containsInAnyOrder(new Ip("1.0.0.1"), _dstIface1Ip, new Ip("2.1.0.0"), new Ip("2.0.0.1")));
  }

  @Test
  public void testBDDTransitions_PreInInterface_NodeDropAclIn() {
    PreInInterface preState = new PreInInterface(_dstName, _link1DstName);
    NodeDropAclIn postState = new NodeDropAclIn(_dstName);
    assertThat(
        bddTransition(preState, postState).biimp(ipBDD(_dstIface1Ip).not()).isOne(), is(true));
  }

  @Test
  public void testBDDTransitions_PreOutVrf_outEdges() {
    String link1SrcName = NET._link1Src.getName();
    String link2SrcName = NET._link2Src.getName();
    PreOutEdgePostNat link1PreOutEdgePostNat =
        new PreOutEdgePostNat(_srcName, link1SrcName, _dstName, _link1DstName);
    PreOutEdgePostNat link2PreOutEdgePostNat =
        new PreOutEdgePostNat(_srcName, link2SrcName, _dstName, _link2DstName);
    PreOutVrf srcPreOutVrf = new PreOutVrf(_srcName, DEFAULT_VRF_NAME);
    BDD nodeDropNullRoute = bddTransition(srcPreOutVrf, new NodeDropNullRoute(_srcName));
    BDD nodeInterfaceNeighborUnreachable1 =
        bddTransition(srcPreOutVrf, new NodeInterfaceNeighborUnreachable(_srcName, link1SrcName));
    BDD nodeInterfaceNeighborUnreachable2 =
        bddTransition(srcPreOutVrf, new NodeInterfaceNeighborUnreachable(_srcName, link2SrcName));
    BDD preOutEdgePostNat1 = bddTransition(srcPreOutVrf, link1PreOutEdgePostNat);
    BDD preOutEdgePostNat2 = bddTransition(srcPreOutVrf, link2PreOutEdgePostNat);

    assertThat(nodeDropNullRoute.isZero(), is(true));

    assertThat(
        bddIps(nodeInterfaceNeighborUnreachable1), contains(NET._link1Src.getAddress().getIp()));
    assertThat(
        bddIps(nodeInterfaceNeighborUnreachable2), contains(NET._link2Src.getAddress().getIp()));

    assertThat(
        bddIps(preOutEdgePostNat1),
        containsInAnyOrder(_dstIface1Ip, _dstIface2Ip, NET._link1Dst.getAddress().getIp()));
    assertThat(
        bddIps(preOutEdgePostNat2),
        containsInAnyOrder(_dstIface2Ip, NET._link2Dst.getAddress().getIp()));

    assertThat(nodeDropNullRoute.and(nodeInterfaceNeighborUnreachable1).isZero(), is(true));
    assertThat(nodeDropNullRoute.and(nodeInterfaceNeighborUnreachable2).isZero(), is(true));
    assertThat(nodeDropNullRoute.and(preOutEdgePostNat1).isZero(), is(true));

    // ECMP: _dstIface1Ip is routed out both edges
    assertThat(
        preOutEdgePostNat1.and(preOutEdgePostNat2).biimp(ipBDD(_dstIface2Ip)).isOne(), is(true));
  }

  @Test
  public void testBDDTransitions_PreOutVrf_NodeInterfaceNeighborUnreachable() {
    /*
     * These predicates include the IP address of the interface, which is technically wrong.
     * It doesn't matter because those addresses can't get to PreOutVrf from PostInVrf.
     */
    assertThat(
        bddIps(
            bddTransition(
                _dstPreOutVrf, new NodeInterfaceNeighborUnreachable(_dstName, _dstIface1Name))),
        contains(_dstIface1Ip));

    assertThat(
        bddIps(
            bddTransition(
                _dstPreOutVrf, new NodeInterfaceNeighborUnreachable(_dstName, _dstIface2Name))),
        contains(new Ip("2.1.0.0")));

    assertThat(
        bddIps(
            bddTransition(
                _dstPreOutVrf, new NodeInterfaceNeighborUnreachable(_dstName, _link1DstName))),
        contains(new Ip("1.0.0.1")));

    assertThat(
        bddIps(
            bddTransition(
                _dstPreOutVrf, new NodeInterfaceNeighborUnreachable(_dstName, _link2DstName))),
        contains(new Ip("2.0.0.1")));
  }

  @Test
  public void testGraph_terminalStates() {
    Set<StateExpr> terminalStates = GRAPH.getTerminalStates();
    assertThat(
        terminalStates,
        equalTo(
            ImmutableSet.of(
                Accept.INSTANCE,
                Drop.INSTANCE,
                NeighborUnreachable.INSTANCE,
                new NodeDropNoRoute(_dstName),
                new NodeDropNoRoute(_srcName))));
  }

  @Test
  public void testGraph_detectMultipathInconsistency() {
    List<MultipathConsistencyViolation> violations = GRAPH.detectMultipathInconsistency();
    assertThat(violations, hasSize(1));
    MultipathConsistencyViolation violation = violations.get(0);
    assertThat(
        bddIps(GRAPH_FACTORY.getApBDDs().get(violation.predicate)),
        equalTo(ImmutableList.of(_dstIface2Ip)));
    assertThat(violation.finalStates, equalTo(ImmutableSet.of(Accept.INSTANCE, Drop.INSTANCE)));
  }
}

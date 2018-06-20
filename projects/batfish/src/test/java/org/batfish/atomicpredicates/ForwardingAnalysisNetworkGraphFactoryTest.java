package org.batfish.atomicpredicates;

import static org.batfish.datamodel.Configuration.DEFAULT_VRF_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.Ip;
import org.batfish.symbolic.bdd.BDDInteger;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.NodeInterfaceNeighborUnreachable;
import org.batfish.z3.state.PostInVrf;
import org.batfish.z3.state.PreOutVrf;
import org.junit.Before;
import org.junit.Test;

public class ForwardingAnalysisNetworkGraphFactoryTest {
  private String _dstIface1Name;
  private String _dstIface2Name;
  private String _dstName;
  private PreOutVrf _dstPreOutVrf;

  private String _link1DstName;
  private String _link2DstName;

  private String _srcName;

  private TwoNodeNetworkWithTwoLinks _net;
  private ForwardingAnalysisNetworkGraphFactory _graphFactory;

  @Before
  public void setup() throws IOException {
    _net = new TwoNodeNetworkWithTwoLinks();
    _net._batfish.computeDataPlane(false);
    DataPlane dataPlane = _net._batfish.loadDataPlane();
    _graphFactory =
        new ForwardingAnalysisNetworkGraphFactory(_net._configs, dataPlane.getForwardingAnalysis());

    _dstIface1Name = _net._dstIface1.getName();
    _dstIface2Name = _net._dstIface2.getName();
    _dstName = _net._dstNode.getHostname();
    _dstPreOutVrf = new PreOutVrf(_dstName, DEFAULT_VRF_NAME);

    _link1DstName = _net._link1Dst.getName();
    _link2DstName = _net._link2Dst.getName();

    _srcName = _net._srcNode.getHostname();
  }

  private List<Ip> bddIps(BDD bdd) {
    BDDInteger bddInteger = _graphFactory.getIpSpaceToBDD().getBDDInteger();

    return bddInteger
        .getValuesSatisfying(bdd, 10)
        .stream()
        .map(Ip::new)
        .collect(Collectors.toList());
  }

  private BDD bddTransition(StateExpr preState, StateExpr postState) {
    return _graphFactory.getBDDTransitions().get(preState).get(postState);
  }

  private BDD vrfAcceptBDD(String node) {
    return _graphFactory.getVrfAcceptBDDs().get(node).get(DEFAULT_VRF_NAME);
  }

  @Test
  public void testVrfAcceptBDDs_dst() {
    List<Ip> ips = bddIps(vrfAcceptBDD(_dstName));
    assertThat(
        ips,
        containsInAnyOrder(
            new Ip("1.0.0.1"), new Ip("2.0.0.1"), new Ip("2.1.0.0"), new Ip("1.1.0.0")));
  }

  @Test
  public void testVrfAcceptBDDs_src() {
    List<Ip> ips = bddIps(vrfAcceptBDD(_srcName));
    assertThat(ips, containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_NodeAccept() {
    PostInVrf preState = new PostInVrf(_srcName, DEFAULT_VRF_NAME);
    NodeAccept postState = new NodeAccept(_srcName);
    List<Ip> ips = bddIps(bddTransition(preState, postState));
    assertThat(ips, containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_PreOutVrf_dst() {
    PostInVrf preState = new PostInVrf(_srcName, DEFAULT_VRF_NAME);
    NodeAccept postState = new NodeAccept(_srcName);
    List<Ip> ips = bddIps(bddTransition(preState, postState));
    assertThat(ips, containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
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
        contains(new Ip("1.1.0.0")));

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
}

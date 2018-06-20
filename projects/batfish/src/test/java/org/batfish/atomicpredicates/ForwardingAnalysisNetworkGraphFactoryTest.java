package org.batfish.atomicpredicates;

import static org.batfish.datamodel.Configuration.DEFAULT_VRF_NAME;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import net.sf.javabdd.BDD;
import org.batfish.datamodel.DataPlane;
import org.batfish.datamodel.Ip;
import org.batfish.symbolic.bdd.BDDInteger;
import org.batfish.z3.state.NodeAccept;
import org.batfish.z3.state.PostInVrf;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

public class ForwardingAnalysisNetworkGraphFactoryTest {

  private TwoNodeNetworkWithTwoLinks _net;
  private ForwardingAnalysisNetworkGraphFactory _graphFactory;

  @Before
  public void setup() throws IOException {
    _net = new TwoNodeNetworkWithTwoLinks();
    _net._batfish.computeDataPlane(false);
    DataPlane dataPlane = _net._batfish.loadDataPlane();
    _graphFactory =
        new ForwardingAnalysisNetworkGraphFactory(_net._configs, dataPlane.getForwardingAnalysis());
  }

  private List<Ip> bddIps(BDD bdd) {
    BDDInteger bddInteger = _graphFactory.getIpSpaceToBDD().getBDDInteger();

    return bddInteger
        .getValuesSatisfying(bdd, 10)
        .stream()
        .map(Ip::new)
        .collect(Collectors.toList());
  }

  @Test
  public void testVrfAcceptBDDs() {
    String srcName = _net._srcNode.getHostname();
    BDD acceptBDD = _graphFactory.getVrfAcceptBDDs().get(srcName).get(DEFAULT_VRF_NAME);
    List<Ip> ips = bddIps(acceptBDD);
    assertThat(ips, Matchers.containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }

  @Test
  public void testBDDTransitions_PostInVrf_NodeAccept() {
    String srcName = _net._srcNode.getHostname();
    BDD bdd =
        _graphFactory
            .getBDDTransitions()
            .get(new PostInVrf(srcName, DEFAULT_VRF_NAME))
            .get(new NodeAccept(srcName));
    List<Ip> ips = bddIps(bdd);
    assertThat(ips, Matchers.containsInAnyOrder(new Ip("1.0.0.0"), new Ip("2.0.0.0")));
  }
}

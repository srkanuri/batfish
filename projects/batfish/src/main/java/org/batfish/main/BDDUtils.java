package org.batfish.main;

import java.util.Map;
import net.sf.javabdd.BDDFactory;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ForwardingAnalysis;
import org.batfish.symbolic.bdd.BDDInteger;
import org.batfish.symbolic.bdd.BDDOps;
import org.batfish.symbolic.bdd.BDDPacket;
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
    _factory = BDDPacket.factory;

    // always use the BDD vars assigned to dstIp by BDDPacket to model dstIps
    _ipAddrBdd = new BDDPacket().getDstIp();

    _ipSpaceToBDD = new IpSpaceToBDD(_factory, _ipAddrBdd);
    _bddOps = new BDDOps(_factory);
    _forwardingAnalysis = forwardingAnalysis;

    _configs = configurations;
  }

  public IpSpaceToBDD getIpSpaceToBDD() {
    return _ipSpaceToBDD;
  }

  public BDDFactory getBDDFactory() {
    return _bddOps.getBDDFactory();
  }
}

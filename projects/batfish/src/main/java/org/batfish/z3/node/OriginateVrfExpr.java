package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class OriginateVrfExpr extends NodePacketRelExpr {

  private static final String BASE_NAME = "R_originate";

  public OriginateVrfExpr(Synthesizer synthesizer, String nodeName, String vrf) {
    super(synthesizer, BASE_NAME, nodeName + "_" + vrf);
  }
}

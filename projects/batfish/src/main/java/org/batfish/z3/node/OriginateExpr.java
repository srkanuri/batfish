package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class OriginateExpr extends NodePacketRelExpr {

  private static final String BASE_NAME = "R_originate";

  public OriginateExpr(Synthesizer synthesizer, String nodeName) {
    super(synthesizer, BASE_NAME, nodeName);
  }
}

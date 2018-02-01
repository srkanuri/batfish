package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class UnoriginalExpr extends NodePacketRelExpr {

  private static final String BASE_NAME = "R_unoriginal";

  public UnoriginalExpr(Synthesizer synthesizer, String nodeName) {
    super(synthesizer, BASE_NAME, nodeName);
  }
}

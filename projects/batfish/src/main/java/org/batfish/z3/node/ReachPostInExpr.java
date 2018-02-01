package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class ReachPostInExpr extends PacketRelExpr {

  private static final String NAME = "ReachPostIn";

  public ReachPostInExpr(Synthesizer synthesizer, String nodeName) {
    super(synthesizer, NAME);
    addArgument(new VarIntExpr(nodeName));
  }
}

package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class AcceptExpr extends PacketRelExpr {

  public static final String NAME = "R_accept";

  public AcceptExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

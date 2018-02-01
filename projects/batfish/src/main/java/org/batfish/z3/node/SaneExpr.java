package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class SaneExpr extends PacketRelExpr {
  private static final String NAME = "Sane";

  public SaneExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

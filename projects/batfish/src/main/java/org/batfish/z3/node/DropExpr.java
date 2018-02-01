package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DropExpr extends PacketRelExpr {

  public static final String NAME = "R_drop";

  public DropExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

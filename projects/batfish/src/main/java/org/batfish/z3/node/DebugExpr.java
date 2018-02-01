package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DebugExpr extends PacketRelExpr {

  public static final String NAME = "R_debug";

  public DebugExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

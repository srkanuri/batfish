package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DropNoRouteExpr extends PacketRelExpr {

  public static final String NAME = "R_drop_no_route";

  public DropNoRouteExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

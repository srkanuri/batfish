package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DropNullRouteExpr extends PacketRelExpr {

  public static final String NAME = "R_drop_null_route";

  public DropNullRouteExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

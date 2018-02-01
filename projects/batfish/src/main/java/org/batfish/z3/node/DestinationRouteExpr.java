package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DestinationRouteExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_destroute";

  public DestinationRouteExpr(Synthesizer synthesizer, String hostname) {
    super(synthesizer, BASE_NAME, hostname);
  }
}

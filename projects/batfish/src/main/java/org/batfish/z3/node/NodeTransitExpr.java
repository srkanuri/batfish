package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NodeTransitExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_node_transit";

  public NodeTransitExpr(Synthesizer synthesizer, String nodeArg) {
    super(synthesizer, BASE_NAME, nodeArg);
  }
}

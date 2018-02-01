package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NodeDropNullRouteExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_node_drop_null_route";

  public NodeDropNullRouteExpr(Synthesizer synthesizer, String nodeArg) {
    super(synthesizer, BASE_NAME, nodeArg);
  }
}

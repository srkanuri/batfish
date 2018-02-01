package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NodeAcceptExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_node_accept";

  public NodeAcceptExpr(Synthesizer synthesizer, String nodeArg) {
    super(synthesizer, BASE_NAME, nodeArg);
  }
}

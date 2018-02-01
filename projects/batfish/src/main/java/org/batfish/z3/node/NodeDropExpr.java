package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NodeDropExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_node_drop";

  public NodeDropExpr(Synthesizer synthesizer, String nodeArg) {
    super(synthesizer, BASE_NAME, nodeArg);
  }
}

package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public abstract class NodePacketRelExpr extends PacketRelExpr {

  public NodePacketRelExpr(Synthesizer synthesizer, String baseName, String nodeName) {
    super(synthesizer,baseName + "_" + nodeName);
  }
}

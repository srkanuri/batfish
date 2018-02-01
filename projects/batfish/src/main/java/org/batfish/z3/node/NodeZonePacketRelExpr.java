package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public abstract class NodeZonePacketRelExpr extends PacketRelExpr {

  public NodeZonePacketRelExpr(Synthesizer synthesizer, String baseName, String nodeName, String zoneName) {
    super(synthesizer, baseName + "_" + nodeName + "_" + zoneName);
  }
}

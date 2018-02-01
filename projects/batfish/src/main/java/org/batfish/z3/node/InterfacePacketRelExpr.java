package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public abstract class InterfacePacketRelExpr extends PacketRelExpr {

  public InterfacePacketRelExpr(Synthesizer synthesizer, String baseName, String nodeName, String interfaceName) {
    super(synthesizer, baseName + "_" + nodeName + "_" + interfaceName);
  }
}

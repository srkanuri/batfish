package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class InboundInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_inbound_interface";

  public InboundInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

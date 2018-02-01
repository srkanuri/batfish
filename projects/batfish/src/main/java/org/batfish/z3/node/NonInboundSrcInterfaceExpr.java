package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NonInboundSrcInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_non_inbound_src_interface";

  public NonInboundSrcInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

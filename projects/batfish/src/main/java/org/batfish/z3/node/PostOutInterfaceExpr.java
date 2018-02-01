package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PostOutInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_postout_interface";

  public PostOutInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

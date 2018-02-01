package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PreOutInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_preout_interface";

  public PreOutInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

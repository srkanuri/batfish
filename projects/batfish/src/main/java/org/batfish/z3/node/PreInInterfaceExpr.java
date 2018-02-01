package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PreInInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_prein_interface";

  public PreInInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

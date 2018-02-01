package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PostInInterfaceExpr extends InterfacePacketRelExpr {

  public static final String BASE_NAME = "R_postin_interface";

  public PostInInterfaceExpr(Synthesizer synthesizer, String nodeName, String interfaceName) {
    super(synthesizer, BASE_NAME, nodeName, interfaceName);
  }
}

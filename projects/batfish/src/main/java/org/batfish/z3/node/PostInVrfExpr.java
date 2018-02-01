package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PostInVrfExpr extends PacketRelExpr {
  public static final String BASE_NAME = "R_postin_vrf";

  public PostInVrfExpr(Synthesizer synthesizer, String nodeName, String vrfName) {
    super(synthesizer,BASE_NAME + "_" + nodeName + "_" + vrfName);
  }
}

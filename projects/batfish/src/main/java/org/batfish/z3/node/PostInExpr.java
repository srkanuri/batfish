package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PostInExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_postin";

  public PostInExpr(Synthesizer synthesizer, String hostname) {
    super(synthesizer, BASE_NAME, hostname);
  }
}

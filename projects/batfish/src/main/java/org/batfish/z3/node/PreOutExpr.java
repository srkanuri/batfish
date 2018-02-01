package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PreOutExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_preout";

  public PreOutExpr(Synthesizer synthesizer, String hostname) {
    super(synthesizer, BASE_NAME, hostname);
  }
}

package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NonInboundNullSrcZoneExpr extends NodePacketRelExpr {

  public static final String BASE_NAME = "R_non_inbound_null_src_zone";

  public NonInboundNullSrcZoneExpr(Synthesizer synthesizer, String nodeName) {
    super(synthesizer, BASE_NAME, nodeName);
  }
}

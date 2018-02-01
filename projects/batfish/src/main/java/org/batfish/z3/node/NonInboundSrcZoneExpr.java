package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NonInboundSrcZoneExpr extends NodeZonePacketRelExpr {

  public static final String BASE_NAME = "R_non_inbound_src_zone";

  public NonInboundSrcZoneExpr(Synthesizer synthesizer, String nodeName, String zoneName) {
    super(synthesizer, BASE_NAME, nodeName, zoneName);
  }
}

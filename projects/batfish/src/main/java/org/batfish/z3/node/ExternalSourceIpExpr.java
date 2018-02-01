package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class ExternalSourceIpExpr extends PacketRelExpr {

  private static final String NAME = "External_source_ip";

  public ExternalSourceIpExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

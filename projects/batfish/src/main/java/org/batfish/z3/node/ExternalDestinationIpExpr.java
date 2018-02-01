package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class ExternalDestinationIpExpr extends PacketRelExpr {

  private static final String NAME = "External_destination_ip";

  public ExternalDestinationIpExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

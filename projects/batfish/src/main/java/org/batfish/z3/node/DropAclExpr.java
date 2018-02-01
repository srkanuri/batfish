package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DropAclExpr extends PacketRelExpr {
  public static final String NAME = "R_drop_acl";

  public DropAclExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

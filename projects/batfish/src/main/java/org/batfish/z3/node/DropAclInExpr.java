package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class DropAclInExpr extends PacketRelExpr {

  public static final String NAME = "R_drop_acl_in";

  public DropAclInExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

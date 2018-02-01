package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class RoleOriginateExpr extends PacketRelExpr {

  private static final String BASENAME = "R_role_originate";

  public RoleOriginateExpr(Synthesizer synthesizer, String roleName) {
    super(synthesizer, BASENAME + "_" + roleName);
  }
}

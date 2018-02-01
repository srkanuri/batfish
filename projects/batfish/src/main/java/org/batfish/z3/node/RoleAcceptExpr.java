package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class RoleAcceptExpr extends PacketRelExpr {

  private static final String BASENAME = "R_role_accept";

  public RoleAcceptExpr(Synthesizer synthesizer, String roleName) {
    super(synthesizer, BASENAME + "_" + roleName);
  }
}

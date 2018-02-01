package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public abstract class PolicyClauseExpr extends PacketRelExpr {

  public PolicyClauseExpr(Synthesizer synthesizer, String baseName, String hostname, String policyName, int clause) {
    super(synthesizer, baseName + "_" + hostname + "_" + policyName + "_" + clause);
  }
}

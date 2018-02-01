package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public abstract class PolicyExpr extends PacketRelExpr {

  public PolicyExpr(Synthesizer synthesizer, String baseName, String hostname, String policyName) {
    super(synthesizer,baseName + "_" + hostname + "_" + policyName);
  }
}

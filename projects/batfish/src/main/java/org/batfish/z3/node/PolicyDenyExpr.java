package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PolicyDenyExpr extends PolicyExpr {

  public static final String BASE_NAME = "D_policy";

  public PolicyDenyExpr(Synthesizer synthesizer, String hostname, String policyName) {
    super(synthesizer, BASE_NAME, hostname, policyName);
  }
}

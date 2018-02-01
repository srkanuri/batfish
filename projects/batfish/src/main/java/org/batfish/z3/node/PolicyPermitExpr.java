package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class PolicyPermitExpr extends PolicyExpr {

  public static final String BASE_NAME = "P_policy";

  public PolicyPermitExpr(Synthesizer synthesizer, String nodeName, String policyName) {
    super(synthesizer, BASE_NAME, nodeName, policyName);
  }
}

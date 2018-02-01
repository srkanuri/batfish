package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class AclMatchExpr extends PolicyClauseExpr {

  public static final String BASE_NAME = "M_acl";

  public AclMatchExpr(Synthesizer synthesizer, String nodeName, String policyName, int clause) {
    super(synthesizer, BASE_NAME, nodeName, policyName, clause);
  }
}

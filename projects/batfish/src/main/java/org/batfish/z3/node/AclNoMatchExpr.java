package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class AclNoMatchExpr extends PolicyClauseExpr {

  public static final String BASE_NAME = "N_acl";

  public AclNoMatchExpr(Synthesizer synthesizer, String nodeName, String policyName, int clause) {
    super(synthesizer, BASE_NAME, nodeName, policyName, clause);
  }
}

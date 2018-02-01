package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class AclDenyExpr extends PolicyExpr {

  public static final String BASE_NAME = "D_acl";

  public AclDenyExpr(Synthesizer synthesizer, String nodeName, String aclName) {
    super(synthesizer, BASE_NAME, nodeName, aclName);
  }
}

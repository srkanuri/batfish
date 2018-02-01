package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class AclPermitExpr extends PolicyExpr {

  public static final String BASE_NAME = "P_acl";

  public AclPermitExpr(Synthesizer synthesizer, String nodeName, String aclName) {
    super(synthesizer, BASE_NAME, nodeName, aclName);
  }
}

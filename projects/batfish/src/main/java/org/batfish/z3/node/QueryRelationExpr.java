package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class QueryRelationExpr extends PacketRelExpr {

  public static final String NAME = "query_relation";

  public QueryRelationExpr(Synthesizer synthesizer) {
    super(synthesizer, NAME);
  }
}

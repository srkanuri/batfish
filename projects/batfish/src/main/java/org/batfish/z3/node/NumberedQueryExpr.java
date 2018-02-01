package org.batfish.z3.node;

import org.batfish.z3.Synthesizer;

public class NumberedQueryExpr extends PacketRelExpr {

  public static final String NAME = "query_relation";

  public NumberedQueryExpr(Synthesizer synthesizer, int number) {
    super(synthesizer, NAME + "_" + number);
  }
}

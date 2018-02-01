package org.batfish.z3.node;

import org.batfish.datamodel.Edge;
import org.batfish.z3.Synthesizer;

public class PreOutEdgeExpr extends PacketRelExpr {

  public static final String BASE_NAME = "R_preout_edge";

  public PreOutEdgeExpr(Synthesizer synthesizer, Edge edge) {
    this(synthesizer, edge.getNode1(), edge.getInt1(), edge.getNode2(), edge.getInt2());
  }

  public PreOutEdgeExpr(Synthesizer synthesizer, String node, String outInt, String nextHop, String inInt) {
    super(synthesizer, BASE_NAME + "_" + node + "_" + outInt + "_" + nextHop + "_" + inInt);
  }
}

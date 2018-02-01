package org.batfish.z3;

import com.microsoft.z3.Z3Exception;
import org.batfish.z3.node.AndExpr;
import org.batfish.z3.node.ExternalDestinationIpExpr;
import org.batfish.z3.node.ExternalSourceIpExpr;
import org.batfish.z3.node.NodeTransitExpr;
import org.batfish.z3.node.QueryExpr;
import org.batfish.z3.node.QueryRelationExpr;
import org.batfish.z3.node.RoleOriginateExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.SaneExpr;

public class RoleTransitQuerySynthesizer extends BaseQuerySynthesizer {

  public RoleTransitQuerySynthesizer(Synthesizer synthesizer, String sourceRole, String transitNode) {
    RoleOriginateExpr roleOriginate = new RoleOriginateExpr(synthesizer, sourceRole);
    NodeTransitExpr nodeTransit = new NodeTransitExpr(synthesizer, transitNode);
    RuleExpr injectSymbolicPackets = new RuleExpr(roleOriginate);
    AndExpr queryConditions = new AndExpr();
    queryConditions.addConjunct(nodeTransit);
    queryConditions.addConjunct(new SaneExpr(synthesizer));
    queryConditions.addConjunct(new ExternalSourceIpExpr(synthesizer));
    queryConditions.addConjunct(new ExternalDestinationIpExpr(synthesizer));
    RuleExpr queryRule = new RuleExpr(queryConditions, new QueryRelationExpr(synthesizer));
    QueryExpr query = new QueryExpr(new QueryRelationExpr(synthesizer));
    StringBuilder sb = new StringBuilder();
    injectSymbolicPackets.print(sb, 0);
    sb.append("\n");
    queryRule.print(sb, 0);
    sb.append("\n");
    query.print(sb, 0);
    sb.append("\n");
  }

  @Override
  public NodProgram getNodProgram(NodProgram baseProgram) throws Z3Exception {
    throw new UnsupportedOperationException("no implementation for generated method");
    // TODO Auto-generated method stub
  }
}

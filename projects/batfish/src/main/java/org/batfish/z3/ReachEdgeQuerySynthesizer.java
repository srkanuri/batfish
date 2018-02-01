package org.batfish.z3;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;
import java.util.List;
import org.batfish.datamodel.Edge;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.z3.node.AcceptExpr;
import org.batfish.z3.node.AndExpr;
import org.batfish.z3.node.OriginateVrfExpr;
import org.batfish.z3.node.PreInInterfaceExpr;
import org.batfish.z3.node.PreOutEdgeExpr;
import org.batfish.z3.node.QueryExpr;
import org.batfish.z3.node.QueryRelationExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.SaneExpr;

public class ReachEdgeQuerySynthesizer extends BaseQuerySynthesizer {

  private Edge _edge;

  private HeaderSpace _headerSpace;

  private String _ingressVrf;

  private String _originationNode;

  private boolean _requireAcceptance;

  private Synthesizer _synthesizer;

  public ReachEdgeQuerySynthesizer(
      Synthesizer synthesizer,
      String originationNode,
      String ingressVrf,
      Edge edge,
      boolean requireAcceptance,
      HeaderSpace headerSpace) {
    _synthesizer = synthesizer;
    _originationNode = originationNode;
    _ingressVrf = ingressVrf;
    _edge = edge;
    _requireAcceptance = requireAcceptance;
    _headerSpace = headerSpace;
  }

  @Override
  public NodProgram getNodProgram(NodProgram baseProgram) throws Z3Exception {
    NodProgram program = new NodProgram(baseProgram.getContext());
    OriginateVrfExpr originate = new OriginateVrfExpr(_synthesizer, _originationNode, _ingressVrf);
    RuleExpr injectSymbolicPackets = new RuleExpr(originate);
    AndExpr queryConditions = new AndExpr();
    queryConditions.addConjunct(new PreOutEdgeExpr(_synthesizer, _edge));
    queryConditions.addConjunct(new PreInInterfaceExpr(_synthesizer, _edge.getNode2(), _edge.getInt2()));
    queryConditions.addConjunct(_synthesizer.matchHeaderSpace(_headerSpace));
    if (_requireAcceptance) {
      queryConditions.addConjunct(new AcceptExpr(_synthesizer));
    }
    queryConditions.addConjunct(new SaneExpr(_synthesizer));
    RuleExpr queryRule = new RuleExpr(queryConditions, new QueryRelationExpr(_synthesizer));
    List<BoolExpr> rules = program.getRules();
    BoolExpr injectSymbolicPacketsBoolExpr = injectSymbolicPackets.toBoolExpr(baseProgram);
    rules.add(injectSymbolicPacketsBoolExpr);
    rules.add(queryRule.toBoolExpr(baseProgram));
    QueryExpr query = new QueryExpr(new QueryRelationExpr(_synthesizer));
    BoolExpr queryBoolExpr = query.toBoolExpr(baseProgram);
    program.getQueries().add(queryBoolExpr);
    return program;
  }
}

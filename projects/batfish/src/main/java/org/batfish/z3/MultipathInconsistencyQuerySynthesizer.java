package org.batfish.z3;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;
import java.util.List;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.z3.node.AcceptExpr;
import org.batfish.z3.node.AndExpr;
import org.batfish.z3.node.DropExpr;
import org.batfish.z3.node.OriginateVrfExpr;
import org.batfish.z3.node.QueryExpr;
import org.batfish.z3.node.QueryRelationExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.SaneExpr;

public class MultipathInconsistencyQuerySynthesizer extends BaseQuerySynthesizer {

  private HeaderSpace _headerSpace;

  private String _hostname;

  private String _vrf;
  private Synthesizer _synthesizer;

  public MultipathInconsistencyQuerySynthesizer(
      Synthesizer synthesizer, String hostname, String vrf, HeaderSpace headerSpace) {
    _hostname = hostname;
    _vrf = vrf;
    _headerSpace = headerSpace;
    _synthesizer = synthesizer;
  }

  @Override
  public NodProgram getNodProgram(NodProgram baseProgram) throws Z3Exception {
    NodProgram program = new NodProgram(baseProgram.getContext());
    OriginateVrfExpr originate = new OriginateVrfExpr(_synthesizer, _hostname, _vrf);
    RuleExpr injectSymbolicPackets = new RuleExpr(originate);
    AndExpr queryConditions = new AndExpr();
    queryConditions.addConjunct(new AcceptExpr(_synthesizer));
    queryConditions.addConjunct(new DropExpr(_synthesizer));
    queryConditions.addConjunct(new SaneExpr(_synthesizer));
    queryConditions.addConjunct(_synthesizer.matchHeaderSpace(_headerSpace));
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

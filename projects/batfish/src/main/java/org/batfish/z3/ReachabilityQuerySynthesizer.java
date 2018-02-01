package org.batfish.z3;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Z3Exception;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.ForwardingAction;
import org.batfish.datamodel.HeaderSpace;
import org.batfish.z3.node.AcceptExpr;
import org.batfish.z3.node.AndExpr;
import org.batfish.z3.node.BooleanExpr;
import org.batfish.z3.node.DebugExpr;
import org.batfish.z3.node.DropAclExpr;
import org.batfish.z3.node.DropAclInExpr;
import org.batfish.z3.node.DropAclOutExpr;
import org.batfish.z3.node.DropExpr;
import org.batfish.z3.node.DropNoRouteExpr;
import org.batfish.z3.node.DropNullRouteExpr;
import org.batfish.z3.node.EqExpr;
import org.batfish.z3.node.IfExpr;
import org.batfish.z3.node.IntExpr;
import org.batfish.z3.node.LitIntExpr;
import org.batfish.z3.node.NodeAcceptExpr;
import org.batfish.z3.node.NodeDropAclExpr;
import org.batfish.z3.node.NodeDropAclInExpr;
import org.batfish.z3.node.NodeDropAclOutExpr;
import org.batfish.z3.node.NodeDropExpr;
import org.batfish.z3.node.NodeDropNoRouteExpr;
import org.batfish.z3.node.NodeDropNullRouteExpr;
import org.batfish.z3.node.NodeTransitExpr;
import org.batfish.z3.node.NotExpr;
import org.batfish.z3.node.OrExpr;
import org.batfish.z3.node.OriginateVrfExpr;
import org.batfish.z3.node.QueryExpr;
import org.batfish.z3.node.QueryRelationExpr;
import org.batfish.z3.node.RuleExpr;
import org.batfish.z3.node.SaneExpr;
import org.batfish.z3.node.VarIntExpr;

public class ReachabilityQuerySynthesizer extends BaseQuerySynthesizer {

  private Set<ForwardingAction> _actions;

  private Set<String> _finalNodes;

  private HeaderSpace _headerSpace;

  private Map<String, Set<String>> _ingressNodeVrfs;

  private Set<String> _transitNodes;

  private Set<String> _notTransitNodes;
  private Synthesizer _synthesizer;

  public ReachabilityQuerySynthesizer(
      Synthesizer synthesizer,
      Set<ForwardingAction> actions,
      HeaderSpace headerSpace,
      Set<String> finalNodes,
      Map<String, Set<String>> ingressNodeVrfs,
      Set<String> transitNodes,
      Set<String> notTransitNodes) {
    _actions = actions;
    _finalNodes = finalNodes;
    _headerSpace = headerSpace;
    _ingressNodeVrfs = ingressNodeVrfs;
    _transitNodes = transitNodes;
    _notTransitNodes = notTransitNodes;
    _synthesizer = synthesizer;
  }

  @Override
  public NodProgram getNodProgram(NodProgram baseProgram) throws Z3Exception {
    NodProgram program = new NodProgram(baseProgram.getContext());

    // create rules for injecting symbolic packets into ingress node(s)
    List<RuleExpr> originateRules = new ArrayList<>();
    int origChoiceBits = _synthesizer.getOriginationChoiceBits();
    int origChoice = 0;
    for (String ingressNode : _ingressNodeVrfs.keySet()) {
      for (String ingressVrf : _ingressNodeVrfs.get(ingressNode)) {
        OriginateVrfExpr originate = new OriginateVrfExpr(_synthesizer, ingressNode, ingressVrf);

        if(origChoiceBits > 0) {
          BooleanExpr guard =
              new EqExpr(
                  new VarIntExpr(Synthesizer.ORIGINATION_CHOICE_VAR),
                  new LitIntExpr(origChoice, origChoiceBits)
                  );
          IfExpr ifExpr = new IfExpr(guard, originate);
          RuleExpr originateRule = new RuleExpr(ifExpr);
          originateRules.add(originateRule);
          origChoice ++;
        } else {
          RuleExpr originateRule = new RuleExpr(originate);
          originateRules.add(originateRule);
        }
      }
    }

    AndExpr queryConditions = new AndExpr();

    // create query condition for action at final node(s)
    OrExpr finalActions = new OrExpr();
    for (ForwardingAction action : _actions) {
      switch (action) {
        case ACCEPT:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeAcceptExpr accept = new NodeAcceptExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(accept);
            }
          } else {
            finalActions.addDisjunct(new AcceptExpr(_synthesizer));
          }
          break;

        case DEBUG:
          finalActions.addDisjunct(new DebugExpr(_synthesizer));
          break;

        case DROP:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropExpr drop = new NodeDropExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropExpr(_synthesizer));
          }
          break;

        case DROP_ACL:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropAclExpr drop = new NodeDropAclExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropAclExpr(_synthesizer));
          }
          break;

        case DROP_ACL_IN:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropAclInExpr drop = new NodeDropAclInExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropAclInExpr(_synthesizer));
          }
          break;

        case DROP_ACL_OUT:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropAclOutExpr drop = new NodeDropAclOutExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropAclOutExpr(_synthesizer));
          }
          break;

        case DROP_NO_ROUTE:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropNoRouteExpr drop = new NodeDropNoRouteExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropNoRouteExpr(_synthesizer));
          }
          break;

        case DROP_NULL_ROUTE:
          if (_finalNodes.size() > 0) {
            for (String finalNode : _finalNodes) {
              NodeDropNullRouteExpr drop = new NodeDropNullRouteExpr(_synthesizer, finalNode);
              finalActions.addDisjunct(drop);
            }
          } else {
            finalActions.addDisjunct(new DropNullRouteExpr(_synthesizer));
          }
          break;

        case FORWARD:
        default:
          throw new BatfishException("unsupported action");
      }
    }
    queryConditions.addConjunct(finalActions);
    queryConditions.addConjunct(new SaneExpr(_synthesizer));

    // check transit constraints (unordered)
    NodeTransitExpr transitExpr = null;
    for (String nodeName : _transitNodes) {
      transitExpr = new NodeTransitExpr(_synthesizer, nodeName);
      queryConditions.addConjunct(transitExpr);
    }
    for (String nodeName : _notTransitNodes) {
      transitExpr = new NodeTransitExpr(_synthesizer, nodeName);
      queryConditions.addConjunct(new NotExpr(transitExpr));
    }

    // add headerSpace constraints
    BooleanExpr matchHeaderSpace = _synthesizer.matchHeaderSpace(_headerSpace);
    queryConditions.addConjunct(matchHeaderSpace);

    RuleExpr queryRule = new RuleExpr(queryConditions, new QueryRelationExpr(_synthesizer));
    List<BoolExpr> rules = program.getRules();
    for (RuleExpr originateRule : originateRules) {
      BoolExpr originateBoolExpr = originateRule.toBoolExpr(baseProgram);
      rules.add(originateBoolExpr);
    }
    rules.add(queryRule.toBoolExpr(baseProgram));
    QueryExpr query = new QueryExpr(new QueryRelationExpr(_synthesizer));
    BoolExpr queryBoolExpr = query.toBoolExpr(baseProgram);
    program.getQueries().add(queryBoolExpr);
    return program;
  }
}
